from pymongo import MongoClient
from pymongo.errors import ServerSelectionTimeoutError

try:
    # 🔹 연결할 URI 입력 (로컬 또는 Atlas)
    client = MongoClient("mongodb://localhost:27017/", serverSelectionTimeoutMS=3000)

    # 🔹 서버에 ping 보내서 살아있는지 테스트
    client.admin.command("ping")
    print("✅ MongoDB 연결 성공!")

    # 🔹 테스트용 DB 선택
    db = client["test_connection_db"]
    col = db["test_collection"]

    # 🔹 테스트 데이터 삽입
    test_doc = {"msg": "connection test", "status": "ok"}
    col.insert_one(test_doc)
    print("✅ 데이터 삽입 성공!")

    # 🔹 삽입된 데이터 조회
    result = col.find_one({"msg": "connection test"})
    print("🔍 조회 결과:", result)

except ServerSelectionTimeoutError:
    print("❌ MongoDB 서버에 연결할 수 없습니다. 서버가 켜져 있는지 확인하세요.")
except Exception as e:
    print("⚠ 오류 발생:", e)
