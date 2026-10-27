# SPACE PICK

촬영·모임·전시·워크숍 공간을 검색하고 예약하는 풀스택 포트폴리오 프로젝트입니다.

## 주요 기능

- 지역·용도·인원·가격별 공간 검색과 상세 조회
- 날짜·시간 선택, 중복 예약 차단, 서버에서 예약 금액 계산
- 토스페이먼츠 테스트 결제·전액 환불과 예약 상태 연결
- 나의 예약 조회·취소, 결제 결과 새로고침 복구
- 회원가입·로그인, 예약 소유권과 운영자 권한 검사
- 운영자 공간 등록·전체 예약 조회

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Frontend | React, TypeScript, Vite |
| Backend | Java 21, Spring Boot, Spring Security, Spring JDBC |
| Database | MySQL 8.4, Flyway |
| Test | JUnit, Testcontainers |
| 운영 구성 | Docker Compose, Caddy, nginx, Prometheus |

## 실행 방법

JDK 21, Node.js 22.12 이상, Docker가 필요합니다. 아래는 Windows PowerShell 기준입니다.

### 1. 환경 설정과 DB 실행

```powershell
git clone https://github.com/jsh10151/Project.git
cd Project
Copy-Item .env.example .env
```

`.env`의 DB 비밀번호와 운영자 비밀번호를 설정한 후 실행합니다. 운영자 비밀번호는 12자 이상으로 지정합니다.

```powershell
docker compose --env-file .env -p space-pick up -d db
```

### 2. 백엔드 실행

프로젝트 루트에서 환경 변수를 불러온 후 실행합니다.

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
    }
}
cd backend
.\gradlew.bat bootRun
```

### 3. 프론트엔드 실행

새 터미널의 `frontend` 폴더에서 실행합니다.

```powershell
npm ci
npm run dev
```

접속 주소: **http://127.0.0.1:5174/**

## 테스트 결제

기본값은 결제 없는 예약 데모입니다. 테스트 결제를 사용하려면 `.env`에 서로 대응하는 토스 API 개별 연동 테스트 키를 설정하고, 환경 변수를 다시 불러온 뒤 백엔드를 재시작합니다.

```dotenv
TOSS_TEST_ENABLED=true
TOSS_TEST_CLIENT_KEY=발급받은_test_ck_키
TOSS_TEST_SECRET_KEY=발급받은_test_sk_키
```

실제 키와 비밀번호는 Git에 올리지 않습니다. 현재 결제 기능은 테스트 모드용입니다.

## 테스트 실행

```powershell
# backend 폴더 — Docker 필요
.\gradlew.bat test

# frontend 폴더
npm run test:navigation
npm run build
```

MySQL 예약 동시성, 결제 승인·환불의 모의 응답, 금액 변조·권한 검사와 URL 정리를 검증합니다.

## 현재 범위

로컬 예약·테스트 결제 흐름을 구현했습니다. 외부 배포는 구성만 준비했으며, 결제 웹훅·자동 대사와 운영 알림은 후속 작업입니다.

운영 설정과 백업·복구 안내: [OPERATIONS.md](OPERATIONS.md)
