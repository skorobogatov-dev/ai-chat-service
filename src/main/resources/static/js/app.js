// AI Chat Application
class ChatApp {
    constructor() {
        this.messagesContainer = document.getElementById('messages');
        this.chatForm = document.getElementById('chatForm');
        this.messageInput = document.getElementById('messageInput');
        this.sendButton = document.getElementById('sendButton');
        this.modelSelect = document.getElementById('modelSelect');
        this.loadingIndicator = null;

        // Predefined questions with different token requirements
        this.quickQuestions = {
            short: "Что такое Kotlin?",
            medium: "Напиши подробное руководство по созданию RESTful API на Ktor с использованием Kotlin. Включи следующие разделы: 1) Настройка проекта с Gradle, добавление всех необходимых зависимостей 2) Структура проекта и архитектурные паттерны (Clean Architecture, слои presentation, domain, data) 3) Создание моделей данных, entities, DTOs, mappers с валидацией через Bean Validation 4) Настройка маршрутов (routing) с примерами CRUD операций для работы с пользователями, заказами и продуктами 5) Подключение к базе данных PostgreSQL через Exposed ORM, настройка connection pool, транзакций, миграций 6) Реализация слоя репозиториев (data access layer) и сервисов с бизнес-логикой 7) JWT аутентификация и авторизация с access и refresh токенами, Spring Security configuration 8) Валидация входных данных на всех уровнях приложения 9) Обработка ошибок и исключений через глобальный exception handler 10) Настройка CORS для работы с фронтендом 11) Логирование запросов и ответов через interceptors 12) Написание unit и integration тестов с использованием JUnit 5, MockK, Testcontainers 13) Dockerизация приложения с multi-stage builds 14) CI/CD pipeline для автоматического деплоя. Для каждого раздела приведи полные примеры кода с детальными комментариями, объясни все важные моменты, best practices и типичные ошибки. Также добавь примеры curl команд для тестирования каждого endpoint и примеры конфигурационных файлов.",
            long: this.generateVeryLongQuestion()
        };

        this.init();
    }

    generateVeryLongQuestion() {
        // Генерируем очень длинный вопрос (> 32k токенов)
        const baseQuestion = `Создай исчерпывающее, детальное и максимально полное техническое руководство по построению современной enterprise-grade распределенной системы на основе микросервисной архитектуры с использованием Kotlin, Spring Boot, Ktor и современных cloud-native технологий. `;

        const detailedSections = `

ЧАСТЬ 1: АРХИТЕКТУРА И ПРОЕКТИРОВАНИЕ СИСТЕМЫ

Раздел 1.1: Декомпозиция монолита и выделение микросервисов
Подробно опиши процесс декомпозиции монолитного приложения на микросервисы. Объясни Domain-Driven Design подход, концепции Bounded Context, Aggregate, Entity, Value Object. Приведи примеры выделения доменов для следующих сервисов: User Service (управление пользователями, профилями, аутентификацией), Order Service (управление заказами, корзиной, чекаутом), Product Service (каталог товаров, категории, атрибуты), Payment Service (обработка платежей, транзакции, возвраты), Inventory Service (управление складом, резервирование товаров), Notification Service (отправка email, SMS, push-уведомлений), Analytics Service (сбор метрик, отчеты, дашборды), Search Service (полнотекстовый поиск, фильтрация, фасеты), Recommendation Service (персональные рекомендации, ML модели).

Раздел 1.2: Паттерны микросервисной архитектуры
Детально разбери и приведи примеры реализации следующих паттернов: API Gateway (маршрутизация, композиция, трансформация запросов), Service Discovery (Eureka, Consul, Kubernetes Service Discovery), Circuit Breaker (Resilience4j, Hystrix), Bulkhead (изоляция ресурсов, thread pools), Rate Limiting (token bucket, leaky bucket), Backend for Frontend (BFF для web, mobile, IoT), Strangler Fig (постепенная миграция с монолита), Saga Pattern (оркестрация vs хореография распределенных транзакций), Event Sourcing (сохранение событий, восстановление состояния), CQRS (разделение команд и запросов, eventual consistency), Outbox Pattern (надежная публикация событий), Two-Phase Commit vs Eventual Consistency.

Раздел 1.3: Дизайн API и контрактов
Опиши процесс проектирования RESTful API: именование ресурсов, использование HTTP методов, коды состояния, версионирование API (URI versioning, header versioning, content negotiation), HATEOAS принципы, пагинация (offset-based, cursor-based, keyset pagination), сортировка и фильтрация, bulk operations, partial responses (GraphQL-like queries в REST), API documentation (OpenAPI/Swagger specification), contract-first vs code-first подходы, contract testing (Pact, Spring Cloud Contract).

ЧАСТЬ 2: РЕАЛИЗАЦИЯ МИКРОСЕРВИСОВ

Раздел 2.1: Настройка multi-module Gradle проекта
Создай детальную структуру multi-module Gradle проекта со следующими модулями: common-lib (shared domain models, utilities, constants), api-contracts (OpenAPI specifications, DTOs), user-service (authentication, user management), order-service (order processing, cart), product-service (product catalog), payment-service (payment processing), inventory-service (stock management), notification-service (email, SMS, push), analytics-service (metrics, reporting), search-service (Elasticsearch integration), recommendation-service (ML-based recommendations), api-gateway (routing, composition), config-server (centralized configuration), service-registry (Eureka server). Для каждого модуля опиши: build.gradle.kts с dependencies, plugins configuration, custom tasks; настройка Kotlin compiler options; integration с Spring Boot; Docker multi-stage builds; настройка Jacoco для code coverage; SpotBugs/Detekt для static analysis; OWASP dependency check.

Раздел 2.2: Реализация User Service
Напиши полный код User Service включающий: Spring Boot application setup с необходимыми аннотациями и конфигурацией; Clean Architecture структуру (presentation/api layer, application/use-cases layer, domain layer, infrastructure/data layer); domain models (User entity с полями: id, username, email, password hash, first name, last name, phone, address, roles, created at, updated at, last login, account status, email verified); DTOs для различных операций (UserRegistrationDto, UserLoginDto, UserProfileDto, UserUpdateDto, ChangePasswordDto); mappers между domain и DTOs (MapStruct configuration); repository layer с Spring Data JPA (методы: findByEmail, findByUsername, findByEmailVerified, search с Specifications); service layer с бизнес-логикой (регистрация, валидация email, хеширование паролей с BCrypt, генерация verification tokens, password reset flow); REST controllers с endpoints: POST /api/v1/users/register, POST /api/v1/users/login, GET /api/v1/users/profile, PUT /api/v1/users/profile, DELETE /api/v1/users/{id}, GET /api/v1/users/search; request validation (Bean Validation аннотации, custom validators); exception handling (custom exceptions, @ControllerAdvice, structured error responses); unit tests (JUnit 5, MockK, fixtures, test data builders); integration tests (Testcontainers, @SpringBootTest, MockMvc); database schema (Flyway migrations); Docker configuration; Kubernetes manifests.

Раздел 2.3: Реализация Order Service
Аналогично User Service, создай полную реализацию Order Service с domain моделями Order, OrderItem, ShoppingCart, Checkout; состояния заказа (CREATED, PENDING_PAYMENT, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED, REFUNDED); state machine для управления переходами состояний; интеграция с Payment Service через REST API и асинхронные события; интеграция с Inventory Service для резервирования товаров; обработка распределенных транзакций через Saga pattern; компенсирующие транзакции при отмене заказа; события (OrderCreatedEvent, OrderPaidEvent, OrderShippedEvent); consumer для событий из других сервисов.

Раздел 2.4: Реализация Payment Service
Создай Payment Service интегрированный с payment gateway (Stripe/PayPal API); обработка различных методов оплаты (credit card, debit card, digital wallets, bank transfer); токенизация платежных данных (PCI DSS compliance); 3D Secure authentication; обработка webhooks от payment providers; retry logic с exponential backoff; idempotency для платежных операций; хранение транзакций (Payment entity со статусами: INITIATED, PENDING, AUTHORIZED, CAPTURED, FAILED, CANCELLED, REFUNDED); reconciliation процесс для сверки транзакций.

Раздел 2.5: Реализация всех остальных сервисов
Для каждого из оставшихся сервисов (Product, Inventory, Notification, Analytics, Search, Recommendation) предоставь аналогично детальную реализацию со всеми слоями, тестами, конфигурацией.

ЧАСТЬ 3: МЕЖСЕРВИСНАЯ КОММУНИКАЦИЯ

Раздел 3.1: Синхронная коммуникация через REST
Настрой Spring Cloud OpenFeign для декларативных REST clients; конфигурация connection timeouts, read timeouts, retry policy; Feign interceptors для добавления authentication headers, correlation IDs, logging; error handling и fallback mechanisms; integration с Resilience4j для circuit breaker, rate limiting, bulkhead; client-side load balancing с Ribbon/Spring Cloud LoadBalancer; HTTP/2 support для улучшения производительности.

Раздел 3.2: Асинхронная коммуникация через message brokers
Настрой Apache Kafka для event streaming: создание topics, partitioning strategy, replication factor, retention policy; producer configuration (acks, idempotence, compression, batching); consumer configuration (consumer groups, offset management, auto-commit vs manual commit, rebalancing); Kafka Streams для stream processing; exactly-once semantics; schema registry (Avro/Protobuf schemas); monitoring Kafka (JMX metrics, Kafka Manager, Confluent Control Center). Также настрой RabbitMQ для task queues: exchanges (direct, topic, fanout, headers), queues, bindings, routing keys; publisher confirms, consumer acknowledgments; dead letter exchanges; message TTL, queue length limits; priority queues; Spring AMQP configuration; monitoring (Management UI, Prometheus exporter).

Раздел 3.3: Service mesh с Istio
Разверни Istio service mesh: установка control plane (istiod), data plane (Envoy sidecars); traffic management (VirtualService, DestinationRule для routing rules, canary deployments, A/B testing, traffic mirroring); security (mTLS между сервисами, authorization policies, JWT validation); observability (distributed tracing с Jaeger, metrics с Prometheus, logging); fault injection для chaos testing; circuit breaking на уровне mesh; rate limiting; retries и timeouts configuration.

ЧАСТЬ 4: АУТЕНТИФИКАЦИЯ И АВТОРИЗАЦИЯ

Раздел 4.1: JWT Authentication
Реализуй полную JWT authentication систему: генерация JWT tokens (access token с коротким TTL, refresh token с длительным TTL); структура JWT claims (iss, sub, aud, exp, iat, jti, custom claims для user ID, username, roles, permissions); подпись токенов (HMAC SHA-256, RSA signatures); верификация токенов; token refresh flow; token revocation (blacklist в Redis); Spring Security configuration (SecurityFilterChain, JwtAuthenticationFilter, JwtAuthenticationProvider); handling authentication failures; CORS configuration для различных origins; CSRF protection для browser-based clients.

Раздел 4.2: OAuth2 и OpenID Connect
Настрой OAuth2 Authorization Server на Spring Authorization Server: настройка registered clients (client ID, client secret, redirect URIs, scopes, grant types); authorization code flow с PKCE; client credentials flow для service-to-service auth; implicit flow (deprecated), device flow; token endpoint; authorization endpoint; introspection endpoint; revocation endpoint; userinfo endpoint для OpenID Connect; настройка scopes и authorities; consent screen; integration с существующей user database; custom claims в ID tokens.

Раздел 4.3: Role-Based Access Control (RBAC)
Разработай систему RBAC: определение ролей (ADMIN, USER, MODERATOR, SUPPORT, ANALYST); определение permissions (READ_USERS, WRITE_USERS, DELETE_USERS, READ_ORDERS, CANCEL_ORDERS и т.д.); маппинг ролей на permissions; hierarchical roles (role inheritance); Spring Security method security (@PreAuthorize, @PostAuthorize, SpEL expressions); custom security evaluators; dynamic permissions loading; caching permissions в Redis; audit logging для security events.

ЧАСТЬ 5: DATA PERSISTENCE

Раздел 5.1: PostgreSQL настройка и оптимизация
Настрой PostgreSQL для production use: connection pooling (HikariCP configuration, pool size calculation, connection timeout, idle timeout, max lifetime); transaction isolation levels (READ COMMITTED, REPEATABLE READ, SERIALIZABLE); indexes (B-tree, Hash, GiST, GIN, BRIN, partial indexes, expression indexes); partitioning (range, list, hash partitioning); vacuuming и autovacuum tuning; analyze и query planning; slow query log; pg_stat_statements для query analysis; replication (streaming replication, logical replication); backup strategies (pg_dump, continuous archiving, point-in-time recovery); performance tuning (shared_buffers, work_mem, maintenance_work_mem, effective_cache_size).

Раздел 5.2: Spring Data JPA
Конфигурируй Spring Data JPA: entity mapping (@Entity, @Table, @Column, @Id, @GeneratedValue, @OneToMany, @ManyToOne, @ManyToMany, @JoinColumn, @JoinTable); embedded entities (@Embeddable, @Embedded); inheritance mapping (SINGLE_TABLE, JOINED, TABLE_PER_CLASS); entity lifecycle callbacks (@PrePersist, @PostPersist, @PreUpdate, @PostUpdate, @PreRemove, @PostRemove); custom repository methods с @Query, Specifications для dynamic queries; pagination и sorting (Pageable, Sort); projections (interface-based, class-based, dynamic projections); entity graphs для eager loading (@EntityGraph); N+1 query problem и solutions (JOIN FETCH, EntityGraph, batch fetching); optimistic locking (@Version); pessimistic locking (LockModeType); audit fields (@CreatedDate, @LastModifiedDate, @CreatedBy, @LastModifiedBy); second-level cache configuration (Ehcache, Redis); query result caching; lazy loading vs eager loading trade-offs.

Раздел 5.3: Database Migrations
Настрой Flyway для database migrations: migration scripts naming convention (V1__Initial_schema.sql, V2__Add_users_table.sql); repeatable migrations (R__Create_views.sql); baseline migration; migration versioning strategy; rollback procedures; migration testing в test environment; data migrations vs schema migrations; handling production migrations (downtime vs zero-downtime migrations, blue-green deployments); migration monitoring и rollback triggers.

ЧАСТЬ 6: CACHING STRATEGIES

Раздел 6.1: Redis кластер
Разверни Redis cluster: cluster setup (master-slave replication, sentinel для automatic failover, cluster mode для sharding); data structures (strings, hashes, lists, sets, sorted sets, streams, HyperLogLog, Bitmaps); TTL policies; eviction policies (LRU, LFU, random, volatile-lru); persistence (RDB snapshots, AOF append-only file); pub/sub для messaging; Lua scripting для atomic operations; Redis transactions (MULTI/EXEC); pipelining для batch operations; monitoring (INFO command, redis-cli --stat, Prometheus Redis Exporter).

Раздел 6.2: Spring Cache
Конфигурируй Spring Cache abstraction: @EnableCaching, @Cacheable, @CachePut, @CacheEvict; cache names и key generation (SpEL expressions, custom key generators); cache managers (ConcurrentMapCacheManager для dev, RedisCacheManager для production); cache configuration (TTL, null value handling, cache statistics); conditional caching (condition, unless); multi-level caching (L1 Caffeine cache, L2 Redis cache); cache-aside vs write-through vs write-behind patterns; cache warming strategies; cache invalidation strategies; monitoring cache hit ratio.

ЧАСТЬ 7: API GATEWAY

Раздел 7.1: Spring Cloud Gateway
Настрой Spring Cloud Gateway: route configuration (predicates для path matching, method matching, header matching, query param matching; filters для modifying requests/responses, adding headers, rate limiting, circuit breaking); предопределенные filters (AddRequestHeader, AddResponseHeader, RewritePath, SetPath, Retry, RequestRateLimiter, CircuitBreaker); custom filters; global filters; WebFlux reactive stack; integration с Service Discovery для dynamic routing; load balancing across service instances; request/response logging; correlation ID propagation; authentication/authorization на gateway level; API composition и aggregation; GraphQL gateway для микросервисов.

Раздел 7.2: Rate Limiting и Throttling
Реализуй rate limiting: token bucket algorithm (tokens per second, bucket capacity); Redis-backed rate limiter для distributed system; rate limiting per user, per IP, per API key; different limits для различных subscription tiers; sliding window rate limiting; rate limit headers (X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset); 429 Too Many Requests response; retry-after header; websocket rate limiting.

ЧАСТЬ 8: OBSERVABILITY

Раздел 8.1: Distributed Tracing
Настрой distributed tracing с Jaeger: Spring Cloud Sleuth для automatic trace propagation; trace ID и span ID generation; trace context propagation (HTTP headers, Kafka message headers, gRPC metadata); Jaeger agent, collector, query service, storage (Elasticsearch, Cassandra); custom spans для important operations; span tags и logs; trace sampling strategies (probabilistic, rate-limiting, adaptive sampling); trace analysis для identifying bottlenecks; integration с APM tools.

Раздел 8.2: Metrics и Monitoring
Конфигурируй метрики: Spring Boot Actuator endpoints; Micrometer для vendor-neutral metrics; Prometheus metrics format (counters, gauges, histograms, summaries); custom metrics (@Timed, @Counted, MeterRegistry); business metrics (orders per minute, revenue, conversion rate); JVM metrics (heap usage, GC pauses, thread count); HTTP request metrics (request rate, latency distribution, error rate); database connection pool metrics; cache metrics; message broker metrics. Настрой Prometheus для сбора метрик: scraping configuration, retention, PromQL queries; alerting rules (AlertManager configuration, alert routing, notification channels - email, Slack, PagerDuty).

Раздел 8.3: Logging
Структурируй логирование: Logback configuration (console appender, file appender, rolling policy, file size/time-based triggers); JSON structured logging (logstash-logback-encoder); log levels per package; MDC (Mapped Diagnostic Context) для correlation ID, user ID, request ID; async logging для performance; centralized logging с ELK stack (Elasticsearch для storage, Logstash/Filebeat для collection, Kibana для visualization); log retention policies; log queries и dashboards в Kibana; log-based alerts.

Раздел 8.4: Dashboards и Visualization
Создай Grafana dashboards: metrics visualization (time series graphs, heatmaps, histograms); dashboard variables для filtering; templating; alert rules в Grafana; панели для JVM metrics, HTTP metrics, business metrics, database metrics; SLO/SLI dashboards (request success rate, latency percentiles, error budget); on-call rotation integration.

ЧАСТЬ 9: TESTING

Раздел 9.1: Unit Testing
Напиши comprehensive unit tests: JUnit 5 setup (@Test, @BeforeEach, @AfterEach, @ParameterizedTest); MockK для mocking (mockk, every, verify, slot, relaxed mocks); test structure (Arrange-Act-Assert, Given-When-Then); testing service layer (mocking repositories, testing business logic); testing mappers; testing validators; property-based testing с Kotest; mutation testing (PITest); code coverage (Jacoco, target 80%+); test fixtures и builders; flaky tests handling.

Раздел 9.2: Integration Testing
Создай integration tests: @SpringBootTest configuration; Testcontainers для PostgreSQL, Redis, Kafka (GenericContainer, wait strategies, container networking); @DynamicPropertySource для dynamic configuration; MockMvc для testing REST API; WebTestClient для reactive endpoints; testing full request-response cycle; database state setup (@BeforeEach с SQL scripts или DBUnit); testing transactions; testing async processing; testing scheduled jobs; REST Assured для API testing.

Раздел 9.3: Contract Testing
Настрой contract testing: Spring Cloud Contract setup (DSL для defining contracts, Groovy/YAML contracts); producer side (contract verification, stub generation); consumer side (stub integration, testing against stubs); Pact для consumer-driven contracts (Pact broker, contract verification, webhook triggers); versioning contracts; breaking change detection; contract testing в CI/CD pipeline.

Раздел 9.4: Performance Testing
Проведи performance testing: JMeter test plans (thread groups, samplers, assertions, listeners); load testing scenarios (normal load, peak load, stress testing, endurance testing, spike testing); distributed testing с JMeter; Gatling для Scala-based tests; k6 для modern performance testing; analyzing results (response times, throughput, error rate, resource utilization); identifying bottlenecks; database profiling; APM tool integration.

Раздел 9.5: E2E Testing
Создай E2E tests: Selenium WebDriver для UI testing; Page Object Model pattern; testing user flows (registration, login, ordering, payment); Cucumber для BDD testing (Gherkin syntax, step definitions); visual regression testing (Percy, Applitools); cross-browser testing (BrowserStack, Sauce Labs); parallel test execution.

ЧАСТЬ 10: DEPLOYMENT И DEVOPS

Раздел 10.1: Dockerization
Создай Docker images: multi-stage Dockerfile (builder stage с Gradle, runtime stage с JRE); base image selection (distroless, Alpine, Ubuntu); layer caching optimization; .dockerignore; health checks (HEALTHCHECK instruction); non-root user; environment variables; secrets management (Docker secrets, external secret managers); Docker Compose для local development (services definition, networks, volumes, dependencies).

Раздел 10.2: Kubernetes Deployment
Разверни на Kubernetes: Deployment manifests (replicas, resource requests/limits, liveness/readiness probes, rolling update strategy, pod disruption budget); Service resources (ClusterIP, NodePort, LoadBalancer); ConfigMaps для configuration; Secrets для sensitive data; PersistentVolumeClaims для stateful applications; Ingress для external access (Nginx Ingress Controller); HorizontalPodAutoscaler для auto-scaling; NetworkPolicies для network segmentation; RBAC configuration; namespace management; Helm charts для package management (values.yaml, templates, chart versioning).

Раздел 10.3: CI/CD Pipeline
Настрой CI/CD: GitLab CI/Jenkins pipeline stages (checkout, build, test, security scan, docker build, docker push, deploy); pipeline triggers (push, merge request, scheduled); environment variables и secrets; artifact storage; test reports (JUnit XML, code coverage reports); static code analysis (SonarQube); dependency vulnerability scanning (OWASP Dependency-Check, Snyk); container scanning (Clair, Trivy); deployment strategies (rolling update, blue-green deployment, canary deployment); approval gates для production; rollback procedures; deployment notifications (Slack, email).

ЧАСТЬ 11: SECURITY

Раздел 11.1: Application Security
Реализуй security best practices: input validation (sanitization, whitelisting); SQL injection prevention (prepared statements, parameterized queries); XSS prevention (escaping output, Content Security Policy); CSRF protection (synchronizer token, double-submit cookie); clickjacking prevention (X-Frame-Options, CSP frame-ancestors); security headers (HSTS, X-Content-Type-Options, Referrer-Policy); secret management (Vault, AWS Secrets Manager, Kubernetes Secrets); sensitive data encryption at rest (database encryption, file encryption); encryption in transit (TLS 1.3, certificate management); dependency vulnerability management.

Раздел 11.2: Infrastructure Security
Настрой infrastructure security: network segmentation (VPC, subnets, security groups, network ACLs); firewall rules (ingress/egress rules, least privilege principle); VPN access для внутренних ресурсов; bastion hosts; IAM roles и policies (least privilege access); audit logging (CloudTrail, Azure Activity Log, GCP Cloud Audit Logs); intrusion detection (IDS/IPS systems, SIEM solutions); DDoS protection (CloudFlare, AWS Shield).

ЧАСТЬ 12: PRODUCTION READINESS

Раздел 12.1: High Availability
Обеспечь high availability: multi-AZ deployment; load balancing (Application Load Balancer, Network Load Balancer); health checks и automatic recovery; database replication (master-slave, multi-master); Redis Sentinel для failover; Kafka replication; stateless services design; session management (distributed sessions в Redis); graceful shutdown (preStop hooks, connection draining); zero-downtime deployments.

Раздел 12.2: Disaster Recovery
Спланируй disaster recovery: backup strategies (automated backups, backup retention, backup testing); recovery point objective (RPO) и recovery time objective (RTO); backup storage (cross-region replication); database PITR (point-in-time recovery); infrastructure as code (Terraform, CloudFormation) для быстрого восстановления; disaster recovery drills; incident response plan; business continuity plan.

Раздел 12.3: Cost Optimization
Оптимизируй costs: right-sizing instances (CPU/memory profiling, choosing appropriate instance types); spot instances/preemptible VMs; reserved instances для predictable workload; autoscaling policies (scale-down агрессивность); resource cleanup (unused volumes, snapshots, old images); database cost optimization (connection pooling, query optimization, read replicas); cache effectiveness; CDN usage; storage lifecycle policies; cost monitoring и alerts.

ДОПОЛНИТЕЛЬНЫЕ ТРЕБОВАНИЯ:
- Для каждого раздела приведи полные примеры рабочего кода на Kotlin
- Добавь подробные комментарии объясняющие каждую строку кода
- Опиши все конфигурационные файлы (application.yml, application.properties, Docker Compose файлы, Kubernetes manifests)
- Приведи примеры curl команд для тестирования каждого API endpoint
- Создай примеры тестов для каждого компонента
- Объясни все архитектурные решения и их trade-offs
- Опиши распространенные ошибки и как их избегать
- Добавь диаграммы архитектуры, sequence diagrams, ER diagrams где необходимо
- Приведи метрики производительности и способы их улучшения
- Опиши процесс мониторинга и troubleshooting в production
- Добавь runbooks для частых операционных задач
- Опиши процесс онбординга новых разработчиков в проект
`;

        // Дублируем детальные секции чтобы точно превысить 32k токенов
        return baseQuestion + detailedSections.repeat(15) + "\n\nПредоставь максимально детальный ответ со всеми примерами кода, конфигурациями, диаграммами и объяснениями.";
    }

    init() {
        this.chatForm.addEventListener('submit', (e) => this.handleSubmit(e));
        this.messageInput.focus();
        this.initQuickQuestionButtons();
    }

    initQuickQuestionButtons() {
        const quickQuestionButtons = document.querySelectorAll('.quick-question-btn');
        quickQuestionButtons.forEach(button => {
            button.addEventListener('click', () => {
                const size = button.dataset.size;
                const question = this.quickQuestions[size];
                if (question) {
                    this.messageInput.value = question;
                    this.messageInput.focus();
                    // Scroll to input
                    this.messageInput.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }
            });
        });
    }

    async handleSubmit(event) {
        event.preventDefault();

        const message = this.messageInput.value.trim();
        if (!message) return;

        // Disable input while processing
        this.setInputState(false);

        // Add user message to chat
        this.addMessage(message, 'user');

        // Clear input
        this.messageInput.value = '';

        // Show loading indicator
        this.showLoadingIndicator();

        try {
            // Get selected model
            const selectedModel = this.modelSelect.value;

            // Send message to API
            const response = await this.sendMessage(message, selectedModel);

            // Hide loading indicator
            this.hideLoadingIndicator();

            // Add assistant response to chat with model info and stats
            this.addMessage(response.response, 'assistant', response.model, {
                inputTokens: response.inputTokens,
                outputTokens: response.outputTokens,
                totalTokens: response.totalTokens,
                responseTimeMs: response.responseTimeMs
            });
        } catch (error) {
            // Hide loading indicator
            this.hideLoadingIndicator();

            console.error('Error:', error);
            this.addMessage(
                `Ошибка: ${error.message || 'Не удалось получить ответ'}`,
                'error'
            );
        } finally {
            // Re-enable input
            this.setInputState(true);
            this.messageInput.focus();
        }
    }

    async sendMessage(message, model) {
        const requestBody = { message };
        if (model) {
            requestBody.model = model;
        }

        const response = await fetch('/api/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(requestBody)
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Server error');
        }

        return await response.json();
    }

    addMessage(text, type, model = null, stats = null) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${type}`;

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';

        const label = type === 'user' ? 'Вы' : type === 'error' ? 'Ошибка' : 'Assistant';
        let modelInfo = '';
        if (model && type === 'assistant') {
            const modelName = this.getModelDisplayName(model);
            modelInfo = ` <span class="model-badge">${modelName}</span>`;
        }
        contentDiv.innerHTML = `<strong>${label}:${modelInfo}</strong> ${this.escapeHtml(text)}`;

        messageDiv.appendChild(contentDiv);

        // Add stats if available
        if (stats && type === 'assistant') {
            const statsDiv = document.createElement('div');
            statsDiv.className = 'message-stats';
            statsDiv.innerHTML = `
                <div class="stats-item">
                    <span class="stats-label">Вход:</span>
                    <span class="stats-value">${stats.inputTokens}</span>
                </div>
                <div class="stats-item">
                    <span class="stats-label">Выход:</span>
                    <span class="stats-value">${stats.outputTokens}</span>
                </div>
                <div class="stats-item">
                    <span class="stats-label">Всего:</span>
                    <span class="stats-value">${stats.totalTokens}</span>
                </div>
                <div class="stats-item">
                    <span class="stats-label">Время:</span>
                    <span class="stats-value">${(stats.responseTimeMs / 1000).toFixed(2)}с</span>
                </div>
            `;
            messageDiv.appendChild(statsDiv);
        }

        this.messagesContainer.appendChild(messageDiv);

        // Scroll to bottom
        this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
    }

    getModelDisplayName(modelId) {
        const modelNames = {
            'claude-3-haiku-20240307': 'Haiku',
            'claude-sonnet-4-20250514': 'Sonnet 4'
        };
        return modelNames[modelId] || modelId;
    }

    setInputState(enabled) {
        this.messageInput.disabled = !enabled;
        this.sendButton.disabled = !enabled;
        this.sendButton.textContent = enabled ? 'Отправить' : 'Отправка...';
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    showLoadingIndicator() {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message assistant loading';
        messageDiv.id = 'loading-indicator';

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';

        contentDiv.innerHTML = `
            <strong>Assistant:</strong>
            <div class="typing-indicator">
                <span></span>
                <span></span>
                <span></span>
            </div>
        `;

        messageDiv.appendChild(contentDiv);
        this.messagesContainer.appendChild(messageDiv);
        this.loadingIndicator = messageDiv;

        // Scroll to bottom
        this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
    }

    hideLoadingIndicator() {
        if (this.loadingIndicator) {
            this.loadingIndicator.remove();
            this.loadingIndicator = null;
        }
    }
}

// Initialize app when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    new ChatApp();
});
