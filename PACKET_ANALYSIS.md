# 패킷 분석 기능 가이드

## 개요

M-Inbody는 이제 게임으로부터 수신하는 **모든 패킷 타입을 자동으로 수집하고 분석**합니다.
현재 정의된 12개 타입 외에도 미지의 패킷을 발견하여 새로운 기능을 개발할 수 있습니다.

## 자동 수집 기능

### 📊 실시간 통계 수집

- **모든 패킷 타입** 자동 추적
- **빈도수** 카운팅
- **파싱 성공/실패** 여부 기록
- **Raw 데이터 샘플** 저장 (파싱 실패한 패킷)

### 💾 자동 파일 저장

애플리케이션 실행 중 **30초마다 자동으로** 다음 파일들이 저장됩니다:

**저장 위치:** `~/m-inbody-packets/`

#### 1. JSON 파일 (`packet-stats-latest.json`)
```json
{
  "totalPackets": 15234,
  "uniqueTypes": 25,
  "knownTypes": 12,
  "unknownTypes": 13,
  "typeStats": {
    "20318": {
      "type": 20318,
      "count": 5432,
      "firstSeen": "2026-01-11T15:30:00",
      "lastSeen": "2026-01-11T15:45:00",
      "parsedSuccessfully": true,
      "rawDataSamples": []
    },
    "100999": {
      "type": 100999,
      "count": 123,
      "firstSeen": "2026-01-11T15:30:00",
      "lastSeen": "2026-01-11T15:45:00",
      "parsedSuccessfully": false,
      "rawDataSamples": [
        "7a 4b 00 00 01 00 00 00 ... (256 bytes total)"
      ]
    }
  }
}
```

#### 2. CSV 파일 (`packet-stats-latest.csv`)
```csv
PacketType,PacketName,Count,FirstSeen,LastSeen,Parsed,IsKnown
20318,ATTACK,5432,2026-01-11T15:30:00,2026-01-11T15:45:00,true,true
100043,ACTION,3210,2026-01-11T15:30:00,2026-01-11T15:45:00,true,true
100999,UNKNOWN,123,2026-01-11T15:30:00,2026-01-11T15:45:00,false,false
```

#### 3. Raw 데이터 덤프 (`raw-dump-latest.txt`)
```
================================================================================
M-Inbody Raw Packet Dump
Generated: 2026-01-11T15:45:00
Total Unparsed Types: 5
================================================================================

--------------------------------------------------------------------------------
Packet Type: 100999
Count: 123
First Seen: 2026-01-11T15:30:00
Last Seen: 2026-01-11T15:45:00
Known Type: false
--------------------------------------------------------------------------------

Sample 1:
7a 4b 00 00 01 00 00 00 ff ff ff ff 00 00 00 00 ... (256 bytes total)

Sample 2:
7a 4b 00 00 02 00 00 00 aa bb cc dd 00 00 00 00 ... (128 bytes total)
```

## REST API 사용법

### 1. 통계 조회

```bash
curl http://localhost:5000/api/v1/packet-stats/summary
```

### 2. 수동 저장

```bash
curl -X POST http://localhost:5000/api/v1/packet-stats/save
```

응답:
```json
{
  "success": true,
  "message": "Statistics saved successfully",
  "outputDir": "/Users/username/m-inbody-packets"
}
```

### 3. 통계 초기화

```bash
curl -X DELETE http://localhost:5000/api/v1/packet-stats/clear
```

## 콘솔 로그

### 새로운 패킷 발견 시

애플리케이션이 처음 보는 패킷을 수신하면 콘솔에 즉시 출력됩니다:

```
📦 [NEW KNOWN PACKET] Type: 20318 (ATTACK)
🔍 [NEW UNKNOWN PACKET] Type: 100999 - Not in PacketType enum!
```

### 30초마다 요약 출력

```
📊 Packet statistics saved: 15234 total packets, 25 unique types (13 unknown)
```

## 미지의 패킷 분석 워크플로우

### 1단계: 게임 플레이 & 데이터 수집

1. M-Inbody 실행
2. 마비노기 모바일 게임 실행
3. 다양한 행동 수행 (공격, 스킬 사용, 아이템 사용, 퀘스트 등)
4. 콘솔에서 새로운 패킷 발견 로그 확인

### 2단계: 파일 확인

```bash
cd ~/m-inbody-packets
ls -la
```

출력:
```
packet-stats-latest.json
packet-stats-20260111_154500.json
packet-stats-latest.csv
raw-dump-latest.txt
```

### 3단계: CSV로 빠른 확인

Excel이나 Numbers로 `packet-stats-latest.csv`를 열어 확인:

- **IsKnown=false** 인 항목이 미지의 패킷
- **Count** 컬럼으로 빈도 확인
- **Parsed=false** 인 항목이 파싱 대상

### 4단계: Raw 데이터 분석

`raw-dump-latest.txt`를 열어 16진수 데이터 확인:

```
Packet Type: 100999
Sample 1:
7a 4b 00 00 01 00 00 00 ff ff ff ff ...
```

### 5단계: 패킷 구조 추론

1. 샘플 데이터들을 비교
2. 반복되는 패턴 찾기
3. Little Endian으로 해석
4. 게임 내 행동과 연관성 추측

### 6단계: 파서 구현

`PacketParserService.kt`에 새로운 파서 추가:

```kotlin
enum class PacketType(val code: Int) {
    // 기존 코드...
    NEW_FEATURE(100999),  // 새로 발견한 패킷
}

// parsePacket 메서드에 추가
PacketType.NEW_FEATURE -> parseNewFeature(data)
```

## 예상 가능한 미지의 패킷들

### 게임 시스템별 예상 패킷

- **인벤토리**: 아이템 획득, 아이템 삭제, 인벤토리 정렬
- **퀘스트**: 퀘스트 시작, 진행, 완료, 보상
- **파티**: 파티 초대, 가입, 탈퇴, 파티원 정보
- **채팅**: 채팅 메시지, 귓속말, 파티 채팅
- **이동**: 위치 업데이트, 텔레포트
- **NPC**: NPC 대화, 상점 거래
- **스탯**: 레벨업, 스탯 변경, 경험치 획득
- **길드**: 길드 정보, 길드 채팅
- **PvP**: PvP 시작, PvP 종료, 순위

## 디버깅 팁

### 특정 행동과 패킷 연관짓기

1. 통계 초기화: `curl -X DELETE http://localhost:5000/api/v1/packet-stats/clear`
2. 게임에서 **단일 행동** 수행 (예: 아이템 하나만 사용)
3. 통계 확인: `curl http://localhost:5000/api/v1/packet-stats/summary | jq`
4. 새로 발견된 패킷 타입 확인

### 패킷 타입 코드 범위

현재 알려진 패킷 타입 코드 범위:
- `20000번대`: 게임 액션 (Attack, Self Damage)
- `100000번대`: 상태 업데이트 (Action, HP, Buff, Boss)

미지의 패킷도 이 범위 안에 있을 가능성이 높습니다.

## 주의사항

- 파일은 **자동으로 덮어쓰기**됩니다 (`-latest.json`, `-latest.csv`)
- 타임스탬프 파일은 **30초마다 생성**되므로 디스크 공간 주의
- Raw 데이터는 **파싱 실패한 패킷만** 저장됩니다
- 각 패킷 타입당 **최대 3개 샘플**만 저장됩니다

## 기여하기

새로운 패킷 타입을 발견하고 파싱에 성공하면:

1. `PacketType.kt`에 새 타입 추가
2. `PacketParserService.kt`에 파서 구현
3. DTO 클래스 생성 (`rest/dto/response/`)
4. `DamageProcessingService.kt`에 처리 로직 추가

---

**Happy Packet Hunting! 🔍📦**