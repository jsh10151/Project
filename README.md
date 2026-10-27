# SPACE PICK

> 공간을 발견하고, 당신의 순간을 만드세요.

촬영·모임·전시·워크숍 공간을 검색하고, 날짜와 시간을 선택해 예약하는 풀스택 포트폴리오 프로젝트입니다. 화면에서 시작한 예약을 MySQL 저장과 토스페이먼츠 테스트 결제·환불까지 연결합니다.

## 주요 기능

- **공간 탐색**: 지역·용도·인원·시간당 가격 필터, 공간 사진과 상세 정보
- **예약**: 날짜·시간·인원 검증, 서버 금액 계산, 같은 공간의 중복 시간 예약 차단
- **나의 예약**: 상태별 조회, 취소, 테스트 결제 환불과 예약 상태 연결
- **인증과 권한**: 회원가입·로그인, 세션 인증, 예약 소유권 및 운영자 권한 검사
- **운영자**: 공간 등록, 전체 예약 현황 조회
- **결제 결과 복구**: 서버 주문 조회, 승인 결과 재확인, 새로고침 후 상태 복구
- **주소 정리**: 결제 처리 후 URL에서 결제 키·금액 제거, 페이지 이동 시 결제 경로 정리

## 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| Frontend | React 19, TypeScript, Vite, CSS, Lucide |
| Backend | Java 21, Spring Boot 4.1.1, Spring Security, Spring JDBC |
| Database | MySQL 8.4, Flyway |
| Test | JUnit, Testcontainers, 모의 결제사 HTTP 응답, Node 테스트 |
| 운영 준비 | Docker Compose, Caddy, nginx, Actuator, Prometheus |
| 결제 | 토스페이먼츠 테스트 결제창·승인·전액 취소 API |

## 예약과 결제 설계

### 중복 예약과 금액 변조 방지

예약 생성 시 공간 행을 잠그고 겹치는 예약 시간을 검사합니다. 같은 공간에 동시에 요청이 들어와도 직렬로 검사하며, 가격은 서버의 공간 요금과 이용 시간으로 계산합니다. 요청 키를 이용해 같은 예약 요청의 반복 처리를 방지합니다.

### 결제와 예약 상태 연결

1. 서버가 주문 ID와 금액을 생성하고 예약 시간을 15분간 점유합니다.
2. 테스트 결제창 완료 후 서버가 주문 소유권·금액·만료 여부를 검사하고 승인을 요청합니다.
3. 승인된 주문은 예약 확정으로 표시합니다.
4. 승인 응답을 확인하지 못한 주문은 결제사 조회로 재확인할 수 있습니다.
5. 환불 확인 후 예약을 취소하며, 환불 확인 중에는 공간 점유를 유지합니다.

외부 결제 요청 중에는 DB 트랜잭션을 유지하지 않습니다. 만료된 결제 대기 주문은 가용 시간과 충돌 검사에서 제외합니다. 만료는 조회 시 계산하는 상태이며 별도의 DB 상태 값은 아닙니다.

### 보안과 오류 처리

세션 쿠키와 CSRF 검증을 사용하고, 로그인·회원가입 요청 횟수를 제한합니다. 다른 사용자의 주문·예약 접근 및 운영자 기능 접근을 서버에서 검사합니다. 화면에서는 통신 시간 초과·세션 만료·중복 클릭을 처리합니다.

## 로컬 실행

### 준비 사항

- JDK 21
- Node.js 22.12 이상 및 npm
- Docker Desktop 또는 Docker Compose를 사용할 수 있는 환경

### 1. 저장소와 환경 설정

아래 명령은 Windows PowerShell 기준입니다.

```powershell
git clone https://github.com/jsh10151/Project.git
cd Project
Copy-Item .env.example .env
```

`.env`의 DB 비밀번호와 운영자 비밀번호를 변경합니다. 운영자 비밀번호는 12자 이상으로 설정하세요. 실제 키와 비밀번호가 들어 있는 `.env`는 Git에 포함하지 않습니다.

### 2. MySQL 실행

프로젝트 루트에서 실행합니다.

```powershell
docker compose --env-file .env -p space-pick up -d db
```

MySQL은 로컬 포트 `3307`을 사용합니다.

### 3. 백엔드 실행

같은 터미널에서 `.env` 값을 프로세스 환경 변수로 불러온 후 실행합니다. Spring Boot 실행 시 `.env`는 자동으로 읽히지 않습니다.

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
    }
}
cd backend
.\gradlew.bat bootRun
```

백엔드는 `http://localhost:8081`에서 실행되며 Flyway가 스키마를 적용합니다.

### 4. 프론트엔드 실행

새 터미널을 열어 저장소의 `frontend` 폴더에서 실행합니다.

```powershell
npm ci
npm run dev
```

브라우저에서 **http://127.0.0.1:5174/** 에 접속합니다. 프론트엔드가 `/api` 요청을 백엔드로 전달합니다. 회원가입 후 예약을 이용하며 운영자는 `.env`에 지정한 계정으로 로그인합니다.

## 테스트 결제 설정

기본값은 결제 없는 예약 데모입니다. 토스 테스트 결제를 사용하려면 `.env`를 설정한 후 환경 변수를 다시 불러오고 백엔드를 재시작합니다.

```dotenv
TOSS_TEST_ENABLED=true
TOSS_TEST_CLIENT_KEY=발급받은_API_개별_연동_테스트_클라이언트_키
TOSS_TEST_SECRET_KEY=대응하는_테스트_시크릿_키
```

결제창용으로 서로 대응하는 `test_ck_` / `test_sk_` 키를 사용합니다. 시크릿 키는 서버에서만 사용합니다. 이 프로젝트는 테스트 결제용이며 실결제 운영을 지원한다고 보장하지 않습니다.

## 검증

```powershell
# backend 폴더: MySQL 통합 테스트에 Docker 필요
.\gradlew.bat test

# frontend 폴더
npm run test:navigation
npm run build

# 프로젝트 루트: 실행 중인 로컬 서버의 읽기 요청 점검
node scripts/load-smoke.mjs
```

기존 개발 검증에서 서버 테스트 12개와 URL 정리 테스트 5개, 프론트엔드·백엔드 빌드를 통과했습니다. MySQL 예약 경쟁 조건 및 모의 결제사 응답을 통한 승인·환불·금액 변조·소유권·오류 복구를 검사했습니다.

브라우저에서는 토스 테스트 결제창 호출과 기존 주문의 환불 완료 표시, 새로고침 복구, 페이지 이동 후 URL 정리를 확인했습니다. 모의 테스트와 화면 확인을 실제 결제사의 모든 실패 상황에 대한 종단 검증으로 간주하지 않습니다.

읽기 요청 200개·동시 요청 10개의 로컬 점검에서는 실패 0건, p95 18.5ms를 기록했습니다. 이는 단일 개발 PC의 기본 점검 결과이며 운영 처리량을 의미하지 않습니다.

## 프로젝트 구조

```text
backend/                  Spring Boot API, DB 마이그레이션, 테스트
frontend/                 React 화면, 결제 결과 처리, URL 테스트
deploy/                   Caddy·Prometheus 설정
scripts/                  DB 백업·복구 검증과 읽기 부하 점검
compose.yml               로컬 MySQL
compose.production.yml    외부 배포용 구성
OPERATIONS.md             운영 설정과 검증 기록
IMPROVEMENTS.md            개선 기록
AUTOMATIC-IMPROVEMENTS.md  후속 개선 기록
```

## 구현 범위와 남은 작업

- 외부 서버 배포와 HTTPS 인증서 발급은 아직 진행하지 않았습니다. 배포 구성만 준비했습니다.
- 결제 웹훅, 자동 대사, 장기 장애 복구와 예약 쓰기 부하 검증이 필요합니다.
- 모니터링 수집 설정은 있으나 알림 채널·장기 보관·운영 대시보드는 추가 설정이 필요합니다.
- 개발 DB 백업·별도 복구 검증 스크립트가 있으며, 운영용 암호화·보관 정책·정기 복구 훈련은 후속 작업입니다.
- GitHub Actions CI 실행 검증은 아직 완료하지 않았습니다.

운영 구성과 과거 검증 기록은 [OPERATIONS.md](OPERATIONS.md)를 참고하세요. 해당 문서에는 초기 구현 단계 기록도 포함되어 있습니다.
