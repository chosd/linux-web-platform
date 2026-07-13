# Linux Web Platform

브라우저에서 Docker 컨테이너를 생성·관리하고, xterm.js 터미널과 파일 탐색기, 리소스 및 네트워크 대시보드를 사용하는 내부 개발 도구입니다.

## 기술 스택

- Frontend: React 18, React Router, TypeScript, Vite 8, xterm.js, Recharts, Vitest
- Backend: Spring Boot 3.3, Java 21, Spring WebSocket, SSE, docker-java
- Runtime: Docker Engine API, `ubuntu:24.04`

## 프로젝트 구조

```text
backend/
  src/main/java/com/example/linuxterminal/
    domains/
      container/   # 컨테이너 CRUD, 상태 및 리소스 통계
      dashboard/   # 호스트 리소스
      network/     # Docker 네트워크와 포트
      sftp/        # 컨테이너 파일 관리
      terminal/    # WebSocket 터미널 세션
    global/        # Docker Engine client, 설정, 예외, 필터, SPA fallback
  src/main/resources/application.yml
  src/test/
frontend/
  src/
    app/           # 애플리케이션 진입점과 URL route 상태
    features/      # containers, dashboard, files, terminal
    layouts/
    pages/
    shared/
    styles/
  vite.config.ts
config/application.yml
docker/terminal/Dockerfile
scripts/
```

## 주요 기능

- `/dashboard`, `/containers`, `/containers/{containerName}/{tab}` URL 기반 화면 복원
- 컨테이너 생성, 조회, 수정, 삭제, 시작, 중지, 재시작
- CPU·memory 제한, 포트 binding, volume mount 설정
- 호스트 polling 및 컨테이너 SSE 리소스 차트
- WebSocket 터미널 연결
- FileZilla 스타일 파일 탐색, 업로드 진행·취소, 다운로드, 디렉터리 생성, 이름 변경, 삭제
- Docker bridge 네트워크 조회·생성 및 컨테이너 연결·해제
- light/dark theme

## 사전 준비

- Java 21
- Docker
- pnpm 11

터미널 이미지:

```bash
docker build -t linux-terminal-playground:ubuntu docker/terminal
```

## 개발 실행

백엔드:

```bash
./scripts/dev-backend.sh
```

프런트엔드:

```bash
./scripts/dev-frontend.sh
```

- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080`
- WebSocket: `ws://localhost:8080/ws/terminal`

프런트 환경 변수는 `frontend/.env.local`에 지정할 수 있습니다.

```bash
VITE_API_BASE_URL=http://localhost:8080
VITE_TERMINAL_WS_URL=ws://localhost:8080/ws/terminal
VITE_USER_ID=anonymous
```

## 설정

공통 런타임 설정은 `config/application.yml`에서 관리합니다.

- 업로드 최대 크기: `512MB`
- idle timeout: `30m`
- Docker 제한: CPU `0.5`, memory `256m`, pids `64`
- Docker host: `terminal.docker.host`
- 기본 network: `bridge`
- 컨테이너 사용자: `suser`
- volume bind: 새 컨테이너 생성 화면에서 입력한 호스트 절대 경로를 그대로 사용

## 테스트와 빌드

```bash
cd frontend
pnpm run test
pnpm run build
```

```bash
cd backend
./gradlew test
```

루트 통합 스크립트:

```bash
./scripts/test-all.sh
./scripts/build-all.sh
```

백엔드 `build`는 pnpm install과 Vite build를 수행하고 결과를 Spring Boot static resource로 복사합니다.

## 보안 및 운영 주의사항

- 현재 인증·인가가 없는 내부 개발 도구입니다. 외부 네트워크에 직접 공개하지 마세요.
- `X-User-Id`와 `VITE_USER_ID`는 데이터 구분자이며 인증 수단이 아닙니다.
- Docker daemon 접근 권한은 host의 강한 권한이므로 격리된 개발 노드에서 실행하세요.
- Docker Engine API를 통해 컨테이너를 제어하며 컨테이너 경로 순회와 잘못된 파일명을 차단합니다.
- root 비밀번호는 컨테이너 생성 시 `chpasswd` 표준 입력으로 전달되지만 운영용 secret 저장 기능은 제공하지 않습니다.

## 남은 개선 후보

- 외부 공개 전 인증·인가, 사용자 quota, 감사 로그
- 전용 terminal bridge로 PTY 처리 강화
- 파일 미리보기·편집 및 다중 파일 압축 다운로드
- 네트워크 삭제와 포트 binding 변경 UI
- 브라우저 E2E 및 실제 Docker 통합 테스트
- 컨테이너 이미지 취약점 검사와 패키지 최소화
