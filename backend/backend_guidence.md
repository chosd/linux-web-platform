# Backend Guidance

## Goal

This backend uses a pragmatic domain-driven layered structure.

Avoid over-engineered hexagonal package naming such as `adapter`, `port`, `in`, and `out`.
Prefer shallow, readable packages that make day-to-day feature work fast.

## Package Structure

Base package:

```text
com.example.linuxterminal
```

Recommended structure:

```text
com.example.linuxterminal
├── global
│   ├── config
│   └── docker
└── domains
    ├── container
    │   ├── config
    │   ├── controller
    │   ├── service
    │   ├── repository
    │   ├── dto
    │   └── domain
    ├── sftp
    │   ├── config
    │   ├── controller
    │   ├── service
    │   └── dto
    └── terminal
        ├── config
        ├── controller
        ├── service
        ├── repository
        └── domain
```

## Domain Package Rules

Each domain should follow this structure where applicable:

```text
domains/{domain}
├── controller
├── service
├── repository
├── dto
├── domain
└── config
```

Use only the packages that the domain actually needs.
Do not create empty packages for future use.

### controller

Use for web entry points.

Examples:

- REST controllers
- WebSocket handlers
- request/response mapping logic

Controllers should depend on service interfaces or service classes, not repositories.

### service

Use for business logic, Docker control, orchestration, scheduling, and external process handling.

Keep service methods focused on application behavior.
Docker CLI/API details may live in service when they are specific to one domain.
Shared Docker command helpers should live in `global/docker`.

### repository

Use for data storage and lookup.

This project currently uses file-backed and in-memory repositories.
Do not introduce database abstractions unless the persistence mechanism actually changes.

### dto

Use for API payloads and simple transport records.

Prefer Java `record` for immutable request/response-like data.

### domain

Use for domain state objects that represent meaningful backend concepts.

Examples:

- `ContainerRecord`
- `TerminalSession`

### config

Use for domain-specific Spring bean registration.

Keep explicit `@Bean` registration here when a class is intentionally plain Java and should not be discovered by stereotype scanning.

## Bean Registration

Current convention:

- Controllers use Spring stereotypes such as `@RestController`.
- Configuration classes use `@Configuration`.
- Service/repository implementation classes are registered explicitly through domain config classes when practical.
- Avoid casually adding `@Service`, `@Component`, or `@Repository` to every class.

Example:

```java
@Configuration
public class ContainerConfig {

    @Bean
    ContainerService containerService(
            DockerCommandFactory dockerCommandFactory,
            ContainerNameGenerator containerNameGenerator,
            FileContainerMetadataRepository metadataRepository
    ) {
        return new DockerContainerServiceImpl(dockerCommandFactory, containerNameGenerator, metadataRepository);
    }
}
```

This keeps object creation visible and avoids hidden dependency wiring.

## Dependency Direction

Follow this direction:

```text
controller -> service -> repository
service -> global helpers
config -> concrete implementations
```

Avoid:

```text
repository -> controller
repository -> service
global -> domains
```

Exception:

`global/docker/DockerCommandFactory` currently depends on `domains/container/dto/ResourceLimits`.
If Docker helpers become more broadly shared, move shared resource limit DTOs to `global/docker` or `global/dto`.

## Interface Policy

Use interfaces only when they provide real value.

Good cases:

- A controller depends on a domain service contract.
- A runtime/repository has multiple likely implementations.
- Tests benefit meaningfully from a narrower abstraction.

Avoid creating interfaces only to mirror a single implementation.

Current accepted interfaces:

- `domains.container.service.ContainerService`
- `domains.sftp.service.ContainerFileService`
- `domains.terminal.service.TerminalSessionService`
- `domains.terminal.service.TerminalRuntime`
- `domains.terminal.repository.TerminalSessionRepository`
- `domains.terminal.service.WebSocketMessageSender`

## Naming Rules

Use practical names:

- `ContainerController`
- `ContainerService`
- `DockerContainerServiceImpl`
- `FileContainerMetadataRepository`
- `ContainerRecord`
- `ResourceLimits`

Avoid architecture-heavy names:

- `ContainerInboundAdapter`
- `ContainerOutboundPort`
- `DockerContainerAdapter`
- `UseCase`
- `CommandPort`

## Access Modifier Rules

Default guidance:

- Public classes are acceptable for Spring-managed classes and cross-package collaborators.
- Keep helper methods `private`.
- Use package-private classes only when they are truly internal to one package and not registered from a `config` package.
- Records used across packages should be `public`.

Because config classes instantiate concrete services from sibling packages, most registered implementation classes need to remain `public`.

## Controller Rules

Controllers should:

- Keep request validation close to the request DTO.
- Resolve request headers/path variables/query parameters.
- Delegate behavior to services.
- Return `ResponseEntity` when status codes need to be explicit.

Controllers should not:

- Build Docker commands.
- Read/write files directly.
- Own process lifecycle logic.
- Access repositories directly.

## Service Rules

Services should:

- Own business flow.
- Validate ownership and state transitions.
- Call repositories and global helpers.
- Convert internal state to API-facing DTOs when simple enough.

Services should not:

- Depend on controllers.
- Hide large unrelated flows in a single method.
- Introduce new framework abstractions without a concrete need.

## Repository Rules

Repositories should:

- Encapsulate storage details.
- Return domain objects or simple values.
- Keep serialization/deserialization details local.

Repositories should not:

- Know about HTTP.
- Know about WebSocket.
- Trigger Docker operations.

## Global Package Rules

Use `global` for code that is shared across multiple domains.

Current examples:

- `global/config/TerminalProperties`
- `global/config/SpaWebMvcConfig`
- `global/config/DockerConfig`
- `global/docker/DockerCommandFactory`
- `global/docker/DockerCommandExecutor`
- `global/docker/DockerCommandResult`

Do not place domain-specific business rules in `global`.

## Adding A New Backend Feature

1. Identify the owning domain.
2. Add or update a controller only if there is a new API/WebSocket entry point.
3. Put business logic in `domains/{domain}/service`.
4. Put persistence logic in `domains/{domain}/repository`.
5. Put request/response records in `domains/{domain}/dto`.
6. Put domain state objects in `domains/{domain}/domain`.
7. Register new plain Java services/repositories in `domains/{domain}/config`.
8. Add focused tests under the same domain path in `src/test/java`.

## Testing Guidance

Use focused tests first.

Recommended test locations:

```text
src/test/java/com/example/linuxterminal/domains/{domain}/...
```

Run:

```bash
cd backend
./gradlew compileJava
./gradlew test
```

Note:

`./gradlew test` also runs the frontend build through the current Gradle task chain.

## Static Assets

Frontend build output is copied to:

```text
backend/src/main/resources/static
```

The hashed files under:

```text
backend/src/main/resources/static/assets
```

should stay ignored by Git.
`index.html` may change when frontend asset hashes change.

## Refactoring Rules

When refactoring:

- Prefer package moves over behavior changes.
- Keep API paths stable unless explicitly requested.
- Remove empty directories after moving files.
- Search for stale imports after package moves.
- Run `./gradlew compileJava` before broader testing.
- Run `./gradlew test` before finishing.

Avoid reintroducing:

- `adapter/in`
- `adapter/out`
- `application/port/in`
- `application/port/out`
- unnecessary `UseCase` classes
- one-interface-per-implementation patterns without a practical reason
