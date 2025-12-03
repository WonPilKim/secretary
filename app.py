from flask import Flask, render_template, request, jsonify, redirect, url_for
from pymongo import MongoClient
from pymongo.errors import ServerSelectionTimeoutError
from datetime import datetime
from bson.objectid import ObjectId   # ⭐ 삭제/수정에 필요

app = Flask(__name__)

# ===========================
# MongoDB 연결
# ===========================
try:
    client = MongoClient("mongodb://localhost:27017/", serverSelectionTimeoutMS=3000)
    client.admin.command("ping")
    print("✅ MongoDB 연결 성공!")
except ServerSelectionTimeoutError:
    print("❌ MongoDB 연결 실패! MongoDB 서버가 실행 중인지 확인하세요.")
except Exception as e:
    print("⚠ MongoDB 오류:", e)

db = client["biseo"]
users = db["local"]
memos = db["memos"]   # 메모 컬렉션


# ===========================
# 기본 채팅/명령 처리
# ===========================
def handle_command(cmd: str) -> str:
    cmd = cmd.lower()

    if "안녕" in cmd:
        return "안녕하세요! 무엇을 도와드릴까요?"

    if "시간" in cmd:
        now = datetime.now().strftime('%H:%M:%S')
        return f"현재 시간은 {now} 입니다."

    if "메모" in cmd:
        return "메모 페이지에서 메모를 관리할 수 있어요! (/memos)"

    return "죄송해요, 아직 이해하지 못했어요."


@app.route("/")
def index():
    return render_template("index.html")


@app.route("/command", methods=["POST"])
def command():
    user_input = request.json.get("text", "").strip()

    if not user_input:
        return jsonify({"response": "입력된 내용이 없어요."})

    # 로그 저장
    users.insert_one({
        "input": user_input,
        "created_at": datetime.now()
    })

    # ⭐ 메모라고 입력하면 메모 페이지로 이동시키기
    if user_input.startswith("메모"):
        return jsonify({"redirect": "/memos"})

    # 기존 응답
    response = handle_command(user_input)
    return jsonify({"response": response})


# ===========================
# 메모 페이지들
# ===========================

# 1) 메모 목록 + 검색
@app.route("/memos")
def memo_list():
    # ?q=키워드 로 검색
    q = request.args.get("q", "").strip()

    if q:
        # text 필드에 q가 포함된 문서 검색 (대소문자 구분 X)
        filter_query = {"text": {"$regex": q, "$options": "i"}}
    else:
        filter_query = {}

    cursor = memos.find(filter_query).sort("created_at", -1)
    memo_list = list(cursor)  # 템플릿에서 len() / 반복 사용 편하게 리스트로 변환

    return render_template("memo_list.html", memos=memo_list, q=q)


# 2) 메모 작성 (새 메모)
@app.route("/memos/new", methods=["GET", "POST"])
def memo_new():
    if request.method == "POST":
        text = request.form.get("text", "").strip()

        if not text:
            return render_template(
                "memo_form.html",
                mode="new",
                error="내용을 입력해 주세요.",
                memo=None
            )

        memos.insert_one({
            "text": text,
            "created_at": datetime.now(),
            "updated_at": None,
        })
        return redirect(url_for("memo_list"))

    return render_template("memo_form.html", mode="new", memo=None)


# 3) 메모 수정
@app.route("/memos/<memo_id>/edit", methods=["GET", "POST"])
def memo_edit(memo_id):
    try:
        _id = ObjectId(memo_id)
    except Exception:
        return "잘못된 메모 ID 입니다.", 400

    if request.method == "POST":
        text = request.form.get("text", "").strip()

        if not text:
            memo = memos.find_one({"_id": _id})
            return render_template(
                "memo_form.html",
                mode="edit",
                memo=memo,
                error="내용을 입력해 주세요."
            )

        memos.update_one(
            {"_id": _id},
            {"$set": {"text": text, "updated_at": datetime.now()}}
        )
        return redirect(url_for("memo_list"))

    memo = memos.find_one({"_id": _id})
    if not memo:
        return "해당 메모를 찾을 수 없습니다.", 404

    return render_template("memo_form.html", mode="edit", memo=memo, error=None)


# 4) 메모 삭제
@app.route("/memos/<memo_id>/delete", methods=["POST"])
def memo_delete(memo_id):
    try:
        _id = ObjectId(memo_id)
    except Exception:
        return "잘못된 메모 ID 입니다.", 400

    memos.delete_one({"_id": _id})
    return redirect(url_for("memo_list"))


if __name__ == "__main__":
    app.run(debug=True)
