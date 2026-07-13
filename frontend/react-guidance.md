# React 프로젝트 구조 및 디자인 가이드

이 문서는 `linux-terminal-frontend` 프로젝트를 racofy의 구조/테마 기준에 맞춰 유지하기 위한 기준 문서이다.
대상 프로젝트에서는 SEO와 i18n을 요구사항으로 두지 않는다.

## 1. 기술 스택 기준

- React 18 + TypeScript + Vite 기반 SPA로 구성한다.
- Tailwind CSS v4와 `@tailwindcss/vite` 플러그인을 사용한다.
- 스타일 기준은 `src/styles/globals.css`의 CSS-first `@theme` 토큰과 CSS 변수이다.
- 외부 CDN은 임의로 추가하지 않는다.
- npm/pnpm 의존성은 실제 기능에 필요한 최소 범위만 추가한다.
- 터미널 UI는 `@xterm/xterm`, `@xterm/addon-fit`을 사용한다.

## 2. 디렉토리 구조 기준

현재 구조를 기본 골격으로 유지한다.

```text
src/
  main.tsx
  app/
    app.tsx
    providers.tsx
  layouts/
    app-shell.tsx
  pages/
    dashboard-page.tsx
    container-crud-page.tsx
    file-explorer-page.tsx
  features/
    containers/
      components/
      config/
      lib/
    dashboard/
      components/
      lib/
    files/
      components/
      lib/
    terminal/
      components/
      config/
      lib/
  shared/
    components/
    contexts/
  styles/
    globals.css
```

## 3. 파일 네이밍 규칙

- 모든 파일명은 kebab-case를 사용한다.
- React 컴포넌트 함수명은 PascalCase를 사용한다.
- 커스텀 훅은 `use` 접두사로 시작한다.
- 페이지 컴포넌트는 `src/pages/{page-name}.tsx` 형식을 사용한다.
- 기능 컴포넌트는 `src/features/{feature-name}/components/` 아래에 둔다.
- API 설정은 `src/features/{feature-name}/config/` 아래에 둔다.
- API 호출 클라이언트와 순수 로직은 `src/features/{feature-name}/lib/` 아래에 둔다.

## 4. 앱 부트스트랩 패턴

- `src/main.tsx`
  - `ReactDOM.createRoot(...)` 실행
  - `Providers`로 앱 전역 provider 연결
  - `App` 렌더링
  - `src/styles/globals.css` 로드
- `src/app/providers.tsx`
  - 앱 전체에 필요한 provider만 연결
  - 현재는 `ThemeProvider`를 연결한다.
- `src/app/app.tsx`
  - 앱 수준 화면 전환 상태 관리
  - `AppShell` 렌더링
  - 터미널 화면 전환 처리

## 5. 라우팅 및 메뉴 패턴

현재 프로젝트는 `react-router-dom`을 사용하지 않는다.

- 메뉴 전환은 `src/app/app.tsx`의 `activeMenu`와 `src/layouts/app-shell.tsx`로 처리한다.
- 새 메뉴 화면은 `AppMenuKey` union type에 키를 추가한다.
- `src/layouts/app-shell.tsx`의 `navItems`에 메뉴 라벨과 설명을 추가한다.
- `src/app/app.tsx`에서 `activeMenu` 조건 렌더링을 추가한다.
- 화면 단위 컴포넌트는 `src/pages/`에 둔다.
- 실제 동작은 `src/features/{feature-name}/components/`에 둔다.

Assumption: 딥링크, 브라우저 뒤로가기, 새로고침 후 경로 보존이 필요해지면 그 시점에 `react-router-dom`과 `src/app/route-config.ts` 도입을 검토한다.

## 6. 페이지와 기능 컴포넌트 분리

- 페이지 컴포넌트는 화면 제목, 설명, 레이아웃 진입점만 담당한다.
- 기능 컴포넌트는 실제 동작, 입력 상태, API 호출 연결, 로딩/에러/성공 상태를 담당한다.
- 페이지 컴포넌트에 fetch, WebSocket, xterm 초기화 같은 기능 로직을 넣지 않는다.

현재 기준:

- `src/pages/dashboard-page.tsx` -> `HostResourceDashboard`, `ContainerListPanel`
- `src/pages/container-crud-page.tsx` -> `ContainerCrudTool`
- `src/pages/file-explorer-page.tsx` -> `FileExplorerTool`
- `src/features/terminal/components/terminal-view.tsx` -> xterm/WebSocket 화면

## 7. 상태 관리 패턴

상태는 범위에 따라 분리한다.

- 앱 수준 상태
  - `src/app/app.tsx`
  - `activeMenu`
  - `selectedContainer`
- 앱 전역 UI 상태
  - `src/shared/contexts/theme-context.tsx`
  - `theme`
- 기능 내부 상태
  - 컨테이너 목록, 로딩, 에러, 편집 상태, 생성/수정 입력값
  - 파일 탐색 경로, 업로드 상태, 드래그 상태
  - 터미널 연결 상태, WebSocket, xterm ref
- 설정 값
  - `src/features/containers/config/container-api.ts`
  - `src/features/terminal/config/terminal-config.ts`

개별 기능 상태는 전역 Context에 올리지 않는다. 기능 간 공유가 실제로 필요할 때만 Context 또는 별도 store를 검토한다.

## 8. API 호출 패턴

브라우저 API 호출은 feature lib로 분리한다.

- API base URL과 user ID는 config 파일에서만 읽는다.
- 컴포넌트는 fetch URL을 직접 조립하지 않는다.
- 응답 타입은 API client 파일에서 export한다.
- 에러 메시지는 API client에서 기본 메시지를 만들고 컴포넌트에서 사용자 흐름에 맞게 표시한다.

현재 기준:

- `src/features/containers/config/container-api.ts`
- `src/features/containers/lib/container-api-client.ts`
- `src/features/dashboard/lib/dashboard-api-client.ts`
- `src/features/files/lib/container-file-api-client.ts`

## 9. 테마 시스템 기준

- 기본 테마는 dark이다.
- 다크 테마는 `html.dark`, `html[data-theme="dark"]` CSS 변수 오버라이드로 처리한다.
- 사용자의 테마 선택은 `localStorage`에 저장한다.
- 테마 상태는 `src/shared/contexts/theme-context.tsx`에서 관리한다.
- 앱 provider 연결은 `src/app/providers.tsx`에서 담당한다.
- UI 컴포넌트는 가능한 `var(--color-*)` 토큰 또는 Tailwind utility class를 사용한다.

핵심 토큰:

- `--color-bg`
- `--color-bg-elevated`
- `--color-surface`
- `--color-surface-strong`
- `--color-surface-hover`
- `--color-terminal`
- `--color-border`
- `--color-border-strong`
- `--color-text`
- `--color-text-strong`
- `--color-text-muted`
- `--color-text-subtle`
- `--color-primary`
- `--color-primary-hover`
- `--radius-control`
- `--radius-card`

## 10. Tailwind 및 CSS 사용 규칙

- 새 UI는 Tailwind utility class를 우선 사용한다.
- 전역으로 재사용되는 토큰, body 배경, xterm 보정, legacy class만 `globals.css`에 둔다.
- 기능별로만 쓰이는 스타일은 가능한 컴포넌트 className으로 옮겨 전역 CSS 비대를 줄인다.
- 반복되는 조합이 2회 이상 나오면 `src/shared/components/`로 분리한다.
- 인라인 스타일은 피한다. 단, 동적 width/height처럼 CSS 변수 또는 Tailwind만으로 처리하기 어려운 수치 표현은 제한적으로 허용한다.
- 카드/패널 radius는 과도하게 키우지 않고 `--radius-card` 기준을 따른다.
- 텍스트가 좁은 화면에서 깨지지 않도록 `min-w-0`, `overflow-wrap: anywhere`, `break-words`, `truncate`를 적극 사용한다.

## 11. 입력 UI 규칙

- 일반 입력은 공통 토큰 기반 input 스타일을 사용한다.
- 긴 텍스트 입력이 필요하면 `src/shared/components/textarea.tsx`를 만든다.
- textarea의 최초 높이는 3~4줄 수준으로 제한하고 `resize-y`를 제공한다.
- 제출 버튼은 입력값이 유효하지 않거나 요청 중일 때 disabled 처리한다.
- 민감한 입력은 클라이언트/서버 처리 범위를 화면 흐름에서 명확히 한다.

## 12. 새 기능 추가 체크리스트

1. `src/features/{feature-name}/components/{feature-name}-tool.tsx` 생성
2. API 호출이 있으면 `src/features/{feature-name}/lib/{feature-name}-api-client.ts` 생성
3. 환경값이나 옵션이 있으면 `src/features/{feature-name}/config/`에 분리
4. `src/pages/{feature-name}-page.tsx` 생성
5. `src/layouts/app-shell.tsx`의 `AppMenuKey`, `navItems` 수정
6. `src/app/app.tsx`의 `activeMenu` 렌더링 분기 추가
7. 반복 UI가 있으면 `src/shared/components/`로 분리
8. 테마 토큰이 light/dark 양쪽에서 읽히는지 확인
9. `pnpm build`로 정적 검증

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

- light/dark 테마 전환
- 새로고침 후 테마 유지
- 데스크톱 왼쪽 메뉴탭 표시
- 모바일 메뉴 레이아웃
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
- 다크 단일 테마 기준으로만 색상을 추가

## 16. SEO/i18n 제외 기준

- 이 프로젝트에는 racofy의 SEO sitemap, canonical, route metadata, i18n message 모듈을 도입하지 않는다.
- UI 문구는 현재처럼 컴포넌트 내부 한국어/영어 혼용을 허용한다.
- 향후 다국어 요구사항이 생길 때만 i18n 구조를 별도 작업으로 도입한다.
