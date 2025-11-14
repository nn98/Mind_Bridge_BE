## 채팅 세션 CRUD

```mermaid
flowchart TD
Start([사용자 요청]) --> CheckAuth{JWT Token<br/>존재?}

    CheckAuth -->|No| Unauth["❌ 401 Unauthorized<br/>(필요한 경우)"]
    CheckAuth -->|Yes| ValidateToken{JWT Token<br/>유효?}
    
    ValidateToken -->|No| InvalidToken["❌ 401 Invalid Token"]
    ValidateToken -->|Yes| CheckEndpoint{Endpoint<br/>타입?}
    
    CheckEndpoint -->|POST /session/save| SaveSession["FastAPI → Spring<br/>세션 저장"]
    CheckEndpoint -->|POST /message/save| SaveMessage["FastAPI → Spring<br/>메시지 저장"]
    CheckEndpoint -->|GET /sessions| ListSessions["사용자 세션 목록<br/>조회"]
    CheckEndpoint -->|GET /messages/:id| GetMessages["세션 메시지<br/>조회"]
    CheckEndpoint -->|GET /sessions/:id| GetSession["세션 상세<br/>조회"]
    CheckEndpoint -->|DELETE /sessions/:id| DeleteSession["세션 삭제"]
    
    SaveSession --> ValidateEmail{Email<br/>유효?}
    SaveMessage --> ValidateSessionId{SessionId<br/>유효?}
    ListSessions --> RequireAuth["@PreAuthorize<br/>검사"]
    GetMessages --> CheckAccess{"@chatAuth<br/>권한?"}
    GetSession --> CheckAccess2{"@chatAuth<br/>권한?"}
    DeleteSession --> CheckAccess3{"@chatAuth<br/>권한?"}
    
    ValidateEmail -->|No| ErrorEmail["❌ 400 BadRequest<br/>Invalid Email"]
    ValidateEmail -->|Yes| MapToEntity["DTO → Entity<br/>변환"]
    
    ValidateSessionId -->|No| ErrorSession["❌ 400 BadRequest<br/>Invalid SessionId"]
    ValidateSessionId -->|Yes| MapToEntity2["DTO → Entity<br/>변환"]
    
    RequireAuth -->|isAuthenticated| QueryDB["📊 getUserEmail()"]
    
    CheckAccess -->|No| ForbiddenAccess["❌ 403 Forbidden"]
    CheckAccess -->|Yes| QueryMessages["📦 getMessagesBySessionId()"]
    
    CheckAccess2 -->|No| ForbiddenAccess
    CheckAccess2 -->|Yes| QuerySession["📦 getSessionById()"]
    
    CheckAccess3 -->|No| ForbiddenAccess
    CheckAccess3 -->|Yes| DeleteOp["🗑️ deleteSession()"]
    
    MapToEntity --> SaveDB["💾 Repository.save()"]
    MapToEntity2 --> SaveDB
    QueryDB --> QueryDB2["📊 Repository.query()"]
    
    QueryMessages --> MapperDTO["Entity → DTO<br/>변환"]
    QuerySession --> MapperDTO
    
    SaveDB --> Metrics["📈 DailyMetrics<br/>업데이트"]
    SaveDB --> LogSuccess["✅ 로깅"]
    
    QueryDB2 --> MapperDTO
    Metrics --> Success["✅ 200 OK<br/>+ ResponseEntity"]
    LogSuccess --> Success
    MapperDTO --> Success
    DeleteOp --> SuccessDelete["✅ 200 OK<br/>삭제 완료"]
    
    Success --> End([응답 반환])
    SuccessDelete --> End
    ErrorEmail --> End
    ErrorSession --> End
    ForbiddenAccess --> End
    Unauth --> End
    InvalidToken --> End
    
    style Start fill:#c8e6c9
    style End fill:#ffcccc
    style Unauth fill:#ffebee
    style InvalidToken fill:#ffebee
    style ErrorEmail fill:#ffebee
    style ErrorSession fill:#ffebee
    style ForbiddenAccess fill:#ffebee
    style Success fill:#c8e6c9
    style SuccessDelete fill:#c8e6c9
```

## 에러 처리 흐름

```mermaid
flowchart TD
    Req["HTTP Request 도착"] -->|DispatcherServlet| Filter["SecurityFilter<br/>+ JwtAuthFilter"]
    
    Filter -->|유효한 JWT| Controller["@RestController<br/>메서드"]
    Filter -->|JWT 없음/만료| SecurityErr["🔴 Authentication<br/>Exception"]
    
    Controller -->|정상 실행| Service["비즈니스 로직"]
    Controller -->|@Validated 검증 실패| ValidationErr["🟡 MethodArgumentNotValid"]
    
    Service -->|정상 완료| Return["Response 반환"]
    Service -->|리소스 없음| NotFoundErr["🔴 NotFoundException"]
    Service -->|권한 없음| ForbiddenErr["🔴 ForbiddenException"]
    Service -->|입력값 오류| BadReqErr["🔴 BadRequestException"]
    Service -->|외부 API 오류| ExtErr["🔴 ExternalServiceException"]
    Service -->|중복 값| ConflictErr["🔴 ConflictException"]
    
    SecurityErr -->|@ExceptionHandler| AdviceHandle1["ProblemDetailsAdvice<br/>.handle...()"]
    ValidationErr -->|@ExceptionHandler| AdviceHandle2["ValidationErrorProcessor<br/>.process()"]
    NotFoundErr -->|@ExceptionHandler| AdviceHandle3["ProblemDetailFactory<br/>.createProblemDetail()"]
    ForbiddenErr -->|@ExceptionHandler| AdviceHandle3
    BadReqErr -->|@ExceptionHandler| AdviceHandle3
    ExtErr -->|@ExceptionHandler| AdviceHandle3
    ConflictErr -->|@ExceptionHandler| AdviceHandle3
    
    AdviceHandle1 -->|RFC 7807 Format| ProblemDetail["ProblemDetail<br/>(application/problem+json)"]
    AdviceHandle2 -->|ValidationError[]| ProblemDetail
    AdviceHandle3 -->|ProblemDetail| ProblemDetail
    
    ProblemDetail -->|status + type + title + detail| Client["📡 Client"]
    Return -->|ResponseEntity| Client
    
    style Req fill:#e3f2fd
    style Client fill:#e3f2fd
    style SecurityErr fill:#ffcdd2
    style ValidationErr fill:#fff9c4
    style NotFoundErr fill:#ffcdd2
    style ForbiddenErr fill:#ffcdd2
    style BadReqErr fill:#ffcdd2
    style ExtErr fill:#ffcdd2
    style ConflictErr fill:#ffcdd2
    style ProblemDetail fill:#c8e6c9
    style Return fill:#c8e6c9
```
