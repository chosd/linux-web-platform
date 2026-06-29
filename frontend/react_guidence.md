# React 프로젝트 구조 및 디자인 가이던스

이 문서는 현재 `linux-terminal-frontend` 프로젝트의 React/Vite 구조, 상태 관리, API 분리, Tailwind CSS v4 기반 디자인 토큰, 레이아웃 패턴을 유지하기 위한 기준 문서이다.

## 1. 기술 스택 기준

- React 18 + TypeScript + Vite 기반 SPA로 구성한다.
- 스타일링은 Tailwind CSS v4와 `src/styles/globals.css`의 CSS-first `@theme` 토큰을 기준으로 한다.
- Tailwind는 `@tailwindcss/vite` 플러그인을 사용한다.
- 현재 프로젝트는 `react-router-dom`을 사용하지 않는다. 메뉴 전환은 `src/app/app.tsx`의 로컬 상태와 `src/layouts/app-shell.tsx`로 처리한다.
- 터미널 UI는 `@xterm/xterm`, `@xterm/addon-fit`을 사용한다.
- 외부 CDN은 임의로 추가하지 않는다.
- npm/pnpm 의존성은 실제 기능에 필요한 최소 범위만 추가한다.

## 2. 현재 디렉토리 구조

현재 구조를 기본 골격으로 사용한다.

```text
src/
  main.tsx
  app/
    app.tsx
  layouts/
    app-shell.tsx
  pages/
    container-crud-page.tsx
  features/
    containers/
      components/
        container-crud-tool.tsx
      config/
        container-api.ts
      lib/
        container-api-client.ts
    terminal/
      components/
        terminal-view.tsx
      config/
        terminal-config.ts
      lib/
        terminal-close-message.ts
  shared/
    components/
      button.tsx
      status-badge.tsx
  styles/
    globals.css
```

## 3. 파일 네이밍 규칙

- 모든 파일명은 kebab-case를 사용한다.
- React 컴포넌트 함수명은 PascalCase를 사용한다.
- 타입과 순수 함수는 필요한 파일 가까이에 두되, 재사용되는 API 타입은 `features/{feature-name}/lib/`에 둔다.
- 페이지 컴포넌트는 `src/pages/{page-name}.tsx` 형식을 사용한다.
- 기능 컴포넌트는 `src/features/{feature-name}/components/{feature-name}-*.tsx` 형식을 우선한다.
- API 설정은 `src/features/{feature-name}/config/` 아래에 둔다.
- API 호출 클라이언트와 순수 로직은 `src/features/{feature-name}/lib/` 아래로 분리한다.

## 4. 앱 부트스트랩 패턴

- `src/main.tsx`
  - `ReactDOM.createRoot(...)` 실행
  - `App` 렌더링
  - `src/styles/globals.css` 로드
- `src/app/app.tsx`
  - 앱 수준 화면 전환 상태 관리
  - `AppShell` 렌더링
  - 컨테이너 관리 페이지와 터미널 화면 전환 처리
- `src/layouts/app-shell.tsx`
  - 왼쪽 메뉴탭 레이아웃 담당
  - 메뉴 항목 목록은 `navItems`로 관리

전역 Provider는 현재 사용하지 않는다. 언어, 테마, 인증처럼 앱 전체 상태가 실제로 필요해질 때만 `src/app/providers.tsx`를 추가한다.

## 5. 라우팅 및 메뉴 패턴

현재 프로젝트는 브라우저 URL 라우팅을 사용하지 않는다.

- 새 메뉴 화면은 `AppMenuKey` union type에 키를 추가한다.
- `src/layouts/app-shell.tsx`의 `navItems`에 메뉴 라벨과 설명을 추가한다.
- `src/app/app.tsx`에서 `activeMenu` 조건 렌더링을 추가한다.
- 화면 단위 컴포넌트는 `src/pages/`에 둔다.
- 실제 동작은 `src/features/{feature-name}/components/`에 둔다.

Assumption: URL 기반 딥링크나 새로고침 후 경로 보존이 필요해지면 그 시점에 `react-router-dom`을 도입하고 `route-config.ts` 구조로 전환한다.

## 6. 페이지와 기능 컴포넌트 분리

페이지 컴포넌트는 화면 제목, 설명, 레이아웃 진입점만 담당한다.

현재 예시:

- `src/pages/container-crud-page.tsx`
  - 페이지 제목/설명 렌더링
  - `ContainerCrudTool` 연결

기능 컴포넌트는 실제 동작을 담당한다.

현재 예시:

- `src/features/containers/components/container-crud-tool.tsx`
  - 컨테이너 목록 조회, 생성, 표시명 수정, 삭제, 시작/중지/재시작 상태 관리
  - `container-api-client.ts`의 API 함수 호출
  - `Button`, `StatusBadge` 같은 shared 컴포넌트 조합
- `src/features/terminal/components/terminal-view.tsx`
  - xterm 초기화
  - WebSocket 연결/해제
  - 터미널 리사이즈와 close 메시지 처리

페이지 컴포넌트에 API 호출, WebSocket 처리, 비즈니스 로직을 넣지 않는다.

## 7. 상태 관리 패턴

상태는 범위에 따라 분리한다.

- 앱 수준 상태
  - `src/app/app.tsx`
  - `activeMenu`
  - `selectedContainer`
- 도구/기능 내부 상태
  - 컨테이너 목록, 로딩, 에러, 편집 중 컨테이너, 생성/수정 입력값
  - 터미널 연결 상태, WebSocket, xterm ref
- 설정 값
  - `src/features/containers/config/container-api.ts`
  - `src/features/terminal/config/terminal-config.ts`

개별 기능 상태는 전역 Context에 올리지 않는다. 기능 간 공유가 실제로 필요해질 때만 Context 또는 별도 store를 검토한다.

## 8. API 호출 패턴

브라우저 API 호출은 feature lib로 분리한다.

현재 기준:

- `src/features/containers/lib/container-api-client.ts`
  - `listContainers`
  - `createContainer`
  - `updateContainer`
  - `deleteContainer`
  - `runContainerAction`

규칙:

- API base URL과 user ID는 `config/container-api.ts`에서만 읽는다.
- 컴포넌트는 fetch URL을 직접 조립하지 않는다.
- 응답 타입은 API client 파일에서 export한다.
- 에러 메시지는 컴포넌트에서 사용자 흐름에 맞게 처리한다.

## 9. 디자인 시스템 패턴

현재 프로젝트는 racofy 스타일의 모던 다크모드 토큰을 `src/styles/globals.css`에 정의한다.

- Tailwind v4 CSS-first 방식 사용
  - `@import "tailwindcss";`
  - `@theme { ... }`
- 핵심 색상
  - `--color-app-bg`
  - `--color-app-bg-elevated`
  - `--color-app-surface`
  - `--color-app-surface-strong`
  - `--color-app-primary`
  - `--color-app-accent`
  - `--color-app-border`
- 기존 CSS 변수도 함께 유지한다.
  - `--color-bg`
  - `--color-surface`
  - `--color-border`
  - `--radius-control`
  - `--radius-card`

디자인 규칙:

- 전체 배경은 깊은 차콜/블랙 계열을 유지한다.
- 카드/패널은 배경보다 살짝 밝은 surface 계열을 사용한다.
- 경계선은 `1px solid rgb(255 255 255 / 0.08)` 수준의 미세한 라인을 기본으로 한다.
- 버튼과 입력은 `--radius-control: 12px`를 사용한다.
- 카드와 패널은 `--radius-card: 16px`를 사용한다.
- 반복 UI는 `shared/components`로 분리한다.
- 텍스트가 좁은 화면에서 깨지지 않도록 `min-w-0`, `overflow-wrap: anywhere`, Tailwind의 `min-w-0`, `break-words`, `truncate`를 적극 사용한다.

대표 shared 컴포넌트:

- `src/shared/components/button.tsx`
- `src/shared/components/status-badge.tsx`

## 10. Tailwind 사용 규칙

- 새 UI를 만들 때는 Tailwind utility class를 우선 사용한다.
- 전역으로 재사용되는 토큰, body 배경, xterm 보정, legacy class는 `globals.css`에 둔다.
- 컴포넌트별로 반복되는 조합이 2회 이상 나오면 shared 컴포넌트로 분리한다.
- Tailwind 색상은 `@theme`의 `app-*` 토큰을 우선 사용한다.
  - 예: `bg-app-surface`, `text-app-text`, `border-app-border`, `rounded-card`
- 인라인 스타일은 피한다.
- 외부 CDN 또는 임의 폰트 로딩은 추가하지 않는다.

## 11. 입력 UI 규칙

- 일반 텍스트 입력은 현재처럼 `<input>`과 공통 토큰 스타일을 사용한다.
- 긴 텍스트 입력이 필요해지면 `src/shared/components/textarea.tsx`를 만든다.
- 민감할 수 있는 입력을 처리하는 기능은 클라이언트/서버 처리 여부를 화면 또는 도움말에 명확히 표시한다.
- 제출 버튼은 입력값이 유효하지 않거나 요청 중일 때 disabled 처리한다.

## 12. 새 기능 추가 체크리스트

새 기능 또는 메뉴를 추가할 때는 다음 순서로 진행한다.

1. `src/features/{feature-name}/components/{feature-name}-tool.tsx` 생성
2. API 호출이 있으면 `src/features/{feature-name}/lib/{feature-name}-api-client.ts` 생성
3. 환경값이나 옵션이 있으면 `src/features/{feature-name}/config/`에 분리
4. `src/pages/{feature-name}-page.tsx` 생성
5. `src/layouts/app-shell.tsx`의 `AppMenuKey`, `navItems` 수정
6. `src/app/app.tsx`의 `activeMenu` 렌더링 분기 추가
7. 반복 UI가 있으면 `src/shared/components/`로 분리
8. `pnpm build`로 정적 검증

## 13. import 규칙

- 프로젝트 내부 import는 `/src/...` 절대 경로를 사용한다.
- 같은 feature 내부라도 구조가 명확해지는 경우 `/src/features/...` 경로를 유지한다.
- 상대 경로가 깊어지는 `../../../` 패턴은 피한다.
- 외부 라이브러리 import와 내부 import 사이에는 빈 줄을 둔다.

## 14. 테스트 및 검증 기준

현재 프론트엔드에는 lint/test 스크립트가 없다. 최소 검증은 다음 명령으로 한다.

```bash
pnpm build
```

화면 변경이 크거나 반응형 레이아웃에 영향이 있으면 다음을 추가로 확인한다.

- 데스크톱 왼쪽 메뉴탭 표시
- 모바일에서 메뉴가 상단 가로 탭으로 전환되는지
- 컨테이너 CRUD 버튼 disabled 상태
- 긴 컨테이너 이름과 긴 표시명 줄바꿈
- 터미널 접속 후 xterm 영역 렌더링과 리사이즈
- 백엔드 API 실패 시 에러 배너 표시

## 15. 피해야 할 패턴

- 컴포넌트 내부에서 fetch URL 직접 조립
- 개별 기능 상태를 전역 Context에 저장
- `dist/` 빌드 산출물 직접 수정
- 외부 CDN 임의 추가
- 인라인 스타일 남발
- 기능 로직을 페이지 컴포넌트에 집중
- 새 메뉴 추가 시 `AppShell`만 수정하고 `App` 렌더링 분기 누락
- 파일명에 PascalCase, camelCase, snake_case 혼용
- 백엔드 API 계약을 프론트엔드에서 임의로 추측해 여러 곳에 중복 작성

## 16. 현재 확인한 주요 파일

- `package.json`: React 18, Vite 8, TypeScript, Tailwind CSS v4, `@tailwindcss/vite`, xterm 의존성 확인
- `vite.config.ts`: React plugin, Tailwind plugin, `/src` alias 확인
- `src/main.tsx`: 앱 진입점과 `globals.css` 로드 확인
- `src/app/app.tsx`: 메뉴 상태와 터미널 화면 전환 확인
- `src/layouts/app-shell.tsx`: 왼쪽 메뉴탭 구조 확인
- `src/pages/container-crud-page.tsx`: 컨테이너 관리 페이지 확인
- `src/features/containers/components/container-crud-tool.tsx`: 컨테이너 CRUD UI와 상태 관리 확인
- `src/features/containers/config/container-api.ts`: API base URL, user ID 설정 확인
- `src/features/containers/lib/container-api-client.ts`: 컨테이너 API client 확인
- `src/features/terminal/components/terminal-view.tsx`: xterm, WebSocket 연결 흐름 확인
- `src/features/terminal/config/terminal-config.ts`: 터미널 설정과 welcome message 확인
- `src/features/terminal/lib/terminal-close-message.ts`: WebSocket close reason 메시지 매핑 확인
- `src/shared/components/button.tsx`: 공통 버튼 확인
- `src/shared/components/status-badge.tsx`: 공통 상태 배지 확인
- `src/styles/globals.css`: Tailwind import, `@theme`, racofy 다크모드 토큰, 전역 스타일 확인

## 17. Assumption

- 현재 프로젝트는 URL 라우팅보다 단일 도구형 관리 화면과 터미널 전환이 우선이다.
- 다국어/i18n은 아직 요구사항이 아니므로 도입하지 않았다.
- 라이트 테마는 아직 요구사항이 아니며, 현재 기준은 다크모드 단일 테마이다.
- Tailwind utility class로 점진 전환하되, 기존 CSS class 기반 화면은 유지한다.
