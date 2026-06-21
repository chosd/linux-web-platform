# Web Linux Terminal MVP

브라우저에서 xterm.js 터미널을 열고, 입력을 Spring Boot WebSocket 백엔드로 전달해 세션별 Ubuntu Docker 컨테이너 내부 `/bin/bash`에서 실행하는 1차 MVP입니다.

## 기술 스택

- Frontend: React, TypeScript, Vite, xterm, xterm-addon-fit
- Backend: Spring Boot 3.x, Java 21, Spring WebSocket
- Runtime sandbox: Docker, `ubuntu:24.04`

## 구조

```text
backend/
  src/main/java/com/example/linuxterminal/
    LinuxTerminalApplication.java
    terminal/
      WebSocketConfig.java
      TerminalWebSocketHandler.java
      TerminalSession.java
      TerminalSessionManager.java
      DockerTerminalService.java
      TerminalProperties.java
  src/main/resources/application.yml
frontend/
  src/terminal/TerminalView.tsx
docker/
  terminal/Dockerfile
```

## Docker 이미지 빌드

```bash
docker build -t linux-terminal-playground:ubuntu docker/terminal
```

## 백엔드 실행

```bash
cd backend
mvn spring-boot:run
```

기본 WebSocket endpoint는 다음과 같습니다.

```text
ws://localhost:8080/ws/terminal
```

`backend/src/main/resources/application.yml`에서 이미지, idle timeout, Docker 리소스 제한을 변경할 수 있습니다.

## 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

필요하면 `.env.local`에 WebSocket URL을 지정합니다.

```bash
VITE_TERMINAL_WS_URL=ws://localhost:8080/ws/terminal
```

## 테스트 방법

브라우저 터미널에서 아래 명령을 순서대로 실행합니다.

```bash
pwd
whoami
ls -al
mkdir test
cd test
touch hello.txt
echo hello > hello.txt
cat hello.txt
ps
```

브라우저 새로고침 또는 WebSocket 연결 종료 후 아래 명령으로 컨테이너가 삭제되는지 확인합니다.

```bash
docker ps -a --filter "name=linux-terminal-"
```

백엔드 단위 테스트:

```bash
cd backend
mvn test
```

프론트엔드 빌드 테스트:

```bash
cd frontend
npm run build
```

## 현재 구현 범위

- `/ws/terminal` WebSocket endpoint
- WebSocket 세션별 Docker 컨테이너 생성
- `docker run -i`로 컨테이너 내부 `/bin/bash` 실행
- 브라우저 입력을 컨테이너 bash stdin으로 전달
- 컨테이너 stdout/stderr를 WebSocket으로 전달
- WebSocket 종료, transport error, idle timeout 시 `docker rm -f` 정리
- idle timeout 기본 10분
- 만료 세션 정리 기본 1분 주기
- CPU, memory, pids, network, user, workdir 제한 설정

## 보안 주의사항

- 사용자 입력을 host bash에서 실행하지 않습니다.
- Docker 명령은 고정 인자 리스트로 구성하며 사용자 입력을 Docker 실행 명령에 넣지 않습니다.
- 컨테이너 네트워크는 `--network=none`입니다.
- 컨테이너는 `student` 사용자로 실행합니다.
- 리소스 제한은 `--cpus=0.5`, `--memory=256m`, `--pids-limit=64`입니다.
- 운영 환경에서는 Docker daemon 접근 권한 자체가 강한 권한이므로 별도 격리된 실행 노드에서 운영해야 합니다.
- 현재 MVP는 인증/인가, 세션 quota, 감사 로그, 파일 업로드 제한을 포함하지 않습니다.

## 다음 TODO

- WebSocket 인증/인가 추가
- 사용자별 동시 세션 수 제한
- 컨테이너 생성 실패 메시지의 프론트 표시 개선
- 터미널 resize 정보를 백엔드와 컨테이너 PTY에 전달
- `docker run` 대신 PTY 지원이 있는 Docker Java client 또는 전용 terminal bridge 검토
- 컨테이너 이미지 취약점 스캔 및 패키지 최소화
