# تقرير المراجعة الشاملة — AuraC2

**التاريخ:** 2026-05-12  
**النطاق:** Backend (Java 21 + Spring Boot) + Frontend (React + TypeScript)  
**القيود:** قراءة فقط — لم يُعدَّل أي كود  
**الأوامر المُنفَّذة:** `npx tsc --noEmit` (خطأ واحد), `mvn test` (فشل بسبب Java 11 في البيئة), `npm run build` (فشل بسبب rollup/Linux)

---

## الملخص التنفيذي

| الخطورة | العدد | الأمثلة |
|---------|-------|---------|
| CRITICAL | 1 | تسريب `expectedOutput` للفرق |
| HIGH | 4 | Language validation، SecurityConfig mismatch، @Valid مفقود، PENDING stuck |
| MEDIUM | 4 | SSE على تحديث المسابقة، خطأ TypeScript، تكرار exception packages، تكرار API code |
| LOW / INFO | 6 | ملاحظات تصميمية وتحسينات |

---

## الجزء الأول: هيكل المشروع والبنية العامة

### 1.1 توزيع الـ Modules

المشروع مقسَّم إلى ثلاثة servers مستقلة تشترك في وحدة `shared`:

- **`authServer`** — JWT authentication + refresh token rotation + user management  
- **`contestServer`** — دورة حياة المسابقة + المسائل + توضيحات + SSE  
- **`submissionServer`** — تقديم الحلول + Judge0 integration + RabbitMQ  
- **`shared`** — SSE base infrastructure (`SseEmitterRegistry`, `SsePublisher`, `SseHeartbeatScheduler`)

البنية المعمارية سليمة ومنطقية. الـ shared module يُقلّل تكرار كود SSE بشكل صحيح.

### 1.2 Java Version

المشروع يستلزم **Java 21**. البيئة التجريبية تحتوي على Java 11 فقط، لذا فشل `mvn test` — هذا قيد في بيئة الاختبار وليس خللاً في الكود.

---

## الجزء الثاني: الأمان والمصادقة

### 2.1 ⚠️ HIGH — SecurityConfig مخالف للـ @PreAuthorize

**الملف:** `authServer/config/SecurityConfiguration.java`

```java
// في SecurityConfig:
.requestMatchers("/api/contest/stream", "/api/team/stream").permitAll()
```

```java
// في ContestStreamController:
@PreAuthorize("hasRole('ADMIN')")
public SseEmitter streamContestEvents(...) { ... }

// في TeamStreamController:
@PreAuthorize("hasRole('TEAM')")
public SseEmitter streamTeamEvents(...) { ... }
```

**المشكلة:** الـ SecurityConfig يسمح بالوصول دون مصادقة (`permitAll`)، لكن الـ controller يطلب role محدد. عند انتهاء صلاحية الـ JWT، يُعيد الخادم `403 Forbidden` بدلاً من `401 Unauthorized`، مما يُعطّل منطق التحديث التلقائي في الـ frontend (الذي يبحث عن 401 لتشغيل refresh token).

**التأثير:** SSE streams تنقطع بعد انتهاء صلاحية token وتعجز عن إعادة الاتصال تلقائياً.

**الحل:** إزالة هذين المسارين من `permitAll` أو إضافة `hasRole` في SecurityConfig بدلاً من الاعتماد على `@PreAuthorize` وحده.

### 2.2 ✅ تحقق صحيح — JWT Filter

`JwtAuthFilter` لا يتضمن مسارات الـ stream في قائمة التجاوز، أي أنه يعالج الـ token لهذه المسارات بشكل صحيح.

### 2.3 ✅ تحقق صحيح — تسجيل Admin

`/auth/register` محمي بـ `@PreAuthorize("hasRole('ADMIN')")` ✅

### 2.4 ⚠️ LOW — AdminBootstrapRunner يكتب كلمة المرور في ملف نصي

**الملف:** `authServer/startup/AdminBootstrapRunner.java`

عند إنشاء حساب الـ admin الأول، يُكتَب اسم المستخدم وكلمة المرور الأولية في `admin-account.txt` في جذر المشروع. هذا الملف قد يُحفظ في git أو يُقرأ من قِبل جهة غير مصرّحة.

**الحل:** حذف الملف فور القراءة، أو استخدام logs المشفّرة بدلاً من ملف نصي.

### 2.5 ⚠️ MEDIUM — غياب @Valid على DTOs

**الملفات:**
- `authServer/dto/login/LoginRequest.java` — لا يحتوي على `@NotBlank`
- `authServer/dto/register/RegisterRequest.java` — لا يحتوي على validation annotations
- `contestServer/dto/clarification/ClarificationRequest.java` — لا validation
- `submissionServer/dto/SubmissionRequest.java`:
  ```java
  public record SubmissionRequest(Long contestId, Long problemId, String language, String code) {}
  ```
  لا `@NotNull`، لا `@NotBlank`، لا `@Size`

**التأثير:** يمكن إرسال طلبات فارغة أو null تُسبّب `NullPointerException` أو سلوكاً غير متوقع.

**الحل:** إضافة `@Valid` على parameters الـ controller + validation annotations على الـ DTOs.

### 2.6 ⚠️ HIGH — Callback endpoint بدون حماية

**الملف:** `submissionServer/service/callback/CallbackHandler.java`

لا يوجد IP allowlist ولا HMAC signature verification على `/api/judge/callback`. أي طرف يعرف الـ endpoint يمكنه حقن نتائج وهمية لأي submission.

**الحل:** إضافة HMAC verification أو على الأقل IP allowlist لعناوين Judge0.

---

## الجزء الثالث: 🔴 CRITICAL — تسريب إجابات Test Cases للفرق

**الملف:** `contestServer/dto/testcase/TestCaseResponse.java`

```java
public static TestCaseResponse fromPublicEntity(TestCase entity) {
    return new TestCaseResponse(
        entity.getId(),
        entity.getInputData(),
        entity.getExpectedOutput(),  // ← BUG: يجب أن يكون null للفرق
        entity.isPublic()
    );
}
```

**الملف:** `contestServer/service/TestCaseService.java`

```java
if (isAdmin) {
    return testCases.stream().map(TestCaseResponse::fromEntity).toList();
} else {
    return testCases.stream().map(TestCaseResponse::fromPublicEntity).toList();
    // fromPublicEntity تُعيد expectedOutput بالكامل!
}
```

**التأثير:** الفرق تستطيع رؤية الإجابات الصحيحة لجميع test cases، مما يُفرغ نظام التحكيم من معناه تماماً.

**الحل الفوري (سطر واحد):**
```java
public static TestCaseResponse fromPublicEntity(TestCase entity) {
    return new TestCaseResponse(
        entity.getId(),
        entity.getInputData(),
        null,  // ← لا تُرسَل الإجابة للفرق
        entity.isPublic()
    );
}
```

---

## الجزء الرابع: دورة حياة المسابقة والـ Scheduler

### 4.1 ✅ منطق الوقت المتبقي صحيح

**الملف:** `contestServer/service/ContestLifecycleService.java`

`resolveRemainingMillis` و `resolveEffectiveEndTime` يحسبان `totalPauseMillis` بشكل صحيح:
```java
effectiveEnd = actualStartTime + durationMillis + totalPauseMillis
```
المنطق يتعامل صحيحاً مع فترات الإيقاف المؤقت المتعددة.

### 4.2 ✅ Scheduling دقيق

**الملف:** `contestServer/scheduler/ContestTransitionScheduler.java`

يستخدم `ScheduledExecutorService` لجدولة الانتقالات في وقت محدد بالميلي ثانية. يُعيد الجدولة بعد كل حدث transactional باستخدام `@TransactionalEventListener(phase = AFTER_COMMIT)` ✅

### 4.3 ✅ Row-level Locking صحيح

**الملف:** `contestServer/service/ContestStatusSyncExecutor.java`

يستخدم `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`) لمنع race conditions عند تغيير حالة المسابقة.

### 4.4 ✅ statusLocked وeffectiveState

التمييز بين `persistedStatus` و `effectiveState` مُنفَّذ بشكل سليم. `statusLocked` يمنع تغييرات التحول التلقائي عند الحاجة.

### 4.5 ⚠️ MEDIUM — updateContestDetails لا يُطلق SSE event

**الملف:** `contestServer/service/ContestService.java`

`updateContestDetails` يُحدّث بيانات المسابقة (العنوان، الوصف، المدة) في قاعدة البيانات لكنه لا يستدعي `eventPublisher.publishEvent()`. نتيجة: المتصلون بالـ SSE stream لن يُحدَّثوا عند تغيير تفاصيل المسابقة حتى يُعيدوا الاتصال.

**الحل:** إضافة `applicationEventPublisher.publishEvent(new ContestUpdatedEvent(...))` بعد الحفظ.

### 4.6 ✅ ClarificationService — PAUSED behavior

`contest.getStatus() != ContestStatus.RUNNING` يمنع التوضيحات أثناء الإيقاف المؤقت. هذا قرار تصميمي واضح (ليس خطأً).

---

## الجزء الخامس: نظام التقديم والتحكيم

### 5.1 🔴 HIGH — Language Validation يحدث بعد حفظ الـ Submission

**الملف:** `submissionServer/service/submission/SubmissionService.java` → `submissionServer/queue/submission/SubmissionConsumer.java`

**تسلسل الأحداث:**
1. `SubmissionService.submit()` يحفظ الـ submission بحالة `PENDING` ✅
2. يُرسل رسالة إلى RabbitMQ ✅
3. `SubmissionConsumer.consume()` يستقبل الرسالة ويستدعي `LanguageMapper.convertLanguage()`
4. إذا كانت اللغة غير مدعومة، يرمي `IllegalArgumentException` ❌
5. الـ submission يبقى إلى الأبد بحالة `PENDING` — **لا يُحدَّث إلى `FAILED`**

**إضافياً:** `SubmissionService.submit()` يحفظ الـ submission قبل التحقق من صحة اللغة في الخدمة نفسها.

**الحل:**
```java
// في SubmissionService.submit() — قبل الحفظ:
if (!LanguageMapper.isSupported(language)) {
    throw new ApiException("Unsupported language: " + language, HttpStatus.BAD_REQUEST);
}
```
أو في `SubmissionConsumer`:
```java
try {
    String judgeLanguage = LanguageMapper.convertLanguage(submission.getLanguage());
    // ...
} catch (IllegalArgumentException e) {
    submission.setStatus(SubmissionStatus.FAILED);
    submissionRepository.save(submission);
    return; // لا إعادة للرسالة إلى الـ queue
}
```

### 5.2 ✅ Force Rejudge آمن

**الملف:** `submissionServer/service/rejudge/RejudgeService.java`

`judgeRunId` يُحجَز قبل الـ commit (`incrementAndGetJudgeRunId`)، ثم يُرسَل إلى RabbitMQ `afterCommit`. هذا يمنع race condition بين الـ rejudge والـ callback القديم.

`isStaleCallback` في `Judge0CallbackService` يتحقق من تطابق `judgeRunId` بشكل صحيح ✅

### 5.3 ⚠️ MEDIUM — RejudgeService يُحمّل كل الـ Submissions في الذاكرة

**الملف:** `submissionServer/service/rejudge/RejudgeService.java`

عند rejudge كامل لمسابقة كبيرة، كل الـ submissions تُحمَّل في `List<Submission>`. في مسابقات كبيرة قد يُسبّب هذا OutOfMemoryError.

**الحل:** استخدام pagination أو `Stream` مع `ScrollPosition`.

### 5.4 ⚠️ HIGH — ADMIN يمكنه التقديم كفريق

**الملف:** `submissionServer/controller/SubmissionController.java`

```java
@PreAuthorize("hasAnyRole('TEAM', 'ADMIN')")
public ResponseEntity<SubmissionResponse> submit(...)
```

يسمح للـ admin بتقديم حلول في المسابقة، مما قد يُؤثّر على نتائج الـ scoreboard.

**الحل:** تقييد التقديم لـ `TEAM` فقط، أو التأكد أن submissions الـ admin مستثناة من الـ scoreboard.

### 5.5 ✅ Judge0 Callback — Stale Protection صحيح

`@Transactional(PESSIMISTIC_WRITE)` + فحص `judgeRunId` في `Judge0CallbackService` يمنع تحديثات قديمة من التأثير على نتيجة الـ rejudge ✅

### 5.6 ⚠️ LOW — SubmissionJudgeResult يتراكم بلا حذف

**الملف:** `submissionServer/entity/SubmissionJudgeResult.java`

`@UniqueConstraint` على `(submission_id, judge_run_id, test_case_number)` يسمح بتراكم الصفوف القديمة من كل rejudge. لا يوجد cleanup للبيانات القديمة.

**الحل:** حذف نتائج `judgeRunId` القديمة عند بدء rejudge جديد.

---

## الجزء السادس: بنية SSE

### 6.1 ✅ البنية الأساسية سليمة تماماً

**الملف:** `shared/sse/SseEmitterRegistry.java`

جميع الـ registries الستة تمتد من `SseEmitterRegistry`:
- `ContestSseRegistry` ✅
- `TeamSseRegistry` ✅
- `ClarificationSseRegistry` ✅
- `ClarificationTeamSseRegistry` ✅
- `SubmissionSseRegistry` ✅
- `AdminSubmissionSseRegistry` ✅

`CopyOnWriteArrayList` للـ broadcast emitters و `ConcurrentHashMap` للـ targeted emitters — خيار صحيح لبيئة multi-threaded.

### 6.2 ✅ Heartbeat يصل لجميع الـ Registries

**الملف:** `shared/sse/SseHeartbeatScheduler.java`

```java
@Autowired
private List<SseEmitterRegistry> registries;  // Spring يجمع كل الـ beans تلقائياً
```

`@Scheduled(fixedDelay = 15000)` يُرسل `ping` event لجميع المتصلين كل 15 ثانية ✅

### 6.3 ✅ @TransactionalEventListener صحيح

`ClarificationSseAdapter` و `SubmissionSsePublisher` يستخدمان `TransactionPhase.AFTER_COMMIT` — يمنع إرسال SSE قبل اكتمال الـ transaction ✅

### 6.4 ⚠️ MEDIUM — SubmissionStream لا يُرسل Snapshot أولياً

**الملف:** `submissionServer/controller/SubmissionStreamController.java`

مقارنةً بـ `ContestStreamController` و `TeamStreamController` اللذين يُرسلان snapshot كامل عند الاتصال، `SubmissionStreamController` لا يُرسل أي snapshot. المستخدم الذي يتصل بعد تقديم حل لن يرى حالة الـ submission حتى تأتي تحديثات جديدة.

**الحل:** إرسال حالة الـ submissions الحالية عند الاتصال الأول.

### 6.5 ✅ ClarificationTeamSseRegistry — mapping صحيح

الـ `audienceId` يُبنى من `contestId + userId` لضمان عزل الـ SSE بين الفرق المختلفة ✅

---

## الجزء السابع: Frontend — React + TypeScript

### 7.1 ✅ useContestStream — منطق الاتصال صحيح

**الملف:** `UI/src/hooks/useContestStream.ts`

- Watchdog timer: 30 ثانية ✅
- Exponential backoff عند الانقطاع ✅
- معالجة `ping` event لإعادة ضبط الـ watchdog ✅
- Refresh token تلقائي عند 401 ✅

نفس النمط مُطبَّق في `useClarificationStream.ts` و `useSubmissionStream.ts` ✅

### 7.2 ✅ Team App — يستخدم endpoint صحيح

**الملف:** `UI/src/team/App.tsx`

```typescript
useContestStream({...}, "/api/team/stream")  // ✅ وليس /api/contest/stream
```

### 7.3 ✅ Admin — رؤية المسابقات المُوقفة مؤقتاً

**الملف:** `UI/src/admin/App.tsx`

```typescript
const selectAdminContest = snapshot.active ?? snapshot.paused ?? snapshot.upcoming ?? null;
```

الـ admin يرى المسابقة حتى في حالة `PAUSED` ✅

### 7.4 🔴 TypeScript Error — resizable.tsx

**الملف:** `UI/src/team/components/ui/resizable.tsx:40`

```
error TS2339: Property 'ref' does not exist on type 'PanelResizeHandleProps'
```

خطأ في التوافقية بين النوع المُعرَّف في `react-resizable-panels` والاستخدام الحالي. `npm run build` يفشل بسبب هذا الخطأ (بالإضافة لمشكلة rollup البيئية).

**الحل:** إزالة خاصية `ref` من `PanelResizeHandle` أو استخدام `forwardRef` بشكل صحيح.

### 7.5 ⚠️ MEDIUM — تكرار كود Auth في الـ API Services

**الملفات:**
- `UI/src/admin/services/api.ts`
- `UI/src/team/services/teamApi.ts`

كلا الملفين يحتويان على نفس منطق `refresh token` يدوياً (استدعاء `/auth/refresh` عند 401، إعادة المحاولة). هذا يعني أن أي إصلاح للـ auth logic يجب تطبيقه في مكانين.

**الحل:** استخراج منطق الـ auth في `apiClient.ts` مشترك مع interceptor واحد.

### 7.6 ⚠️ LOW — Admin SubmissionsView يُعيد جلب كل البيانات عند كل SSE event

**الملف:** `UI/src/admin/components/SubmissionsView.tsx`

```typescript
onEvent: () => setRefreshKey(k => k + 1)  // يُطلق full refetch
```

بدلاً من تحديث الـ submission المحدد فقط، يُعيد جلب كل القائمة. آمن لكن غير فعّال في المسابقات الكبيرة.

---

## الجزء الثامن: قاعدة البيانات والـ Entities

### 8.1 ✅ Pessimistic Locking صحيح

`ContestRepository.findByIdWithLock` مع `@Lock(PESSIMISTIC_WRITE)` يمنع التعديلات المتزامنة ✅

### 8.2 ⚠️ LOW — غياب @Index على Submission

**الملف:** `submissionServer/entity/Submission.java`

لا توجد `@Index` annotations على `contestId` أو `teamId` أو `status`. في المسابقات الكبيرة ستكون queries على هذه الحقول بطيئة.

**الحل:**
```java
@Table(name = "submissions", indexes = {
    @Index(columnList = "contest_id"),
    @Index(columnList = "team_id"),
    @Index(columnList = "status")
})
```

### 8.3 ✅ User Entity

`@Column(unique=true)` على `username` ✅. لا مشاكل في الـ entity.

### 8.4 ⚠️ MEDIUM — تكرار Exception Packages

**الملفات:**
- `contestServer/exception/ContestNotFoundException.java`
- `contestServer/exceptions/ContestNotFoundException.java` (مجلد مختلف!)

يوجد `ContestNotFoundException` و `InvalidContestStateException` في مجلدين مختلفين (`exception` و `exceptions`). قد يُسبّب هذا import خاطئ وسلوكاً غير متوقع في الـ exception handling.

**الحل:** دمج المجلدين في مجلد واحد `exception`.

---

## الجزء التاسع: معالجة الأخطاء

### 9.1 ✅ GlobalExceptionHandler — تغطية جيدة

**الملف:** `authServer/exception/GlobalExceptionHandler.java`

يعالج:
- `ApiException` → HTTP status من الـ exception ✅
- `MethodArgumentNotValidException` → 400 ✅
- `AsyncRequestNotUsableException` → تُهمَل بصمت ✅
- `IOException` → 503 ✅
- `Exception` → 500 ✅

### 9.2 ⚠️ HIGH — SubmissionService يرمي RuntimeException مباشرة

**الملف:** `submissionServer/service/submission/SubmissionService.java`

```java
throw new RuntimeException("Contest not found");
throw new RuntimeException("Team is not registered");
```

`RuntimeException` لا تُعالَج بشكل خاص في `GlobalExceptionHandler`، مما يُعيد 500 بدلاً من 404 أو 403.

**الحل:** استبدال `RuntimeException` بـ `ApiException` مع الـ HTTP status المناسب.

### 9.3 ⚠️ MEDIUM — IllegalArgumentException من LanguageMapper لا تُعالَج

`GlobalExceptionHandler` لا يعالج `IllegalArgumentException` صراحةً. هذا يعني 500 للمستخدم بدلاً من 400.

---

## الجزء العاشر: الاختبارات

### 10.1 تغطية محدودة

يوجد 11 ملف اختبار، جميعها unit tests. **لا توجد:**
- Integration tests للـ controllers
- Tests للـ SSE streams
- Tests للـ RabbitMQ pipeline
- Tests للـ frontend (لا Jest، لا Playwright)
- Tests للـ Contest state machine (UPCOMING → RUNNING → PAUSED → RUNNING → ENDED)

### 10.2 ملاحظة بيئة الاختبار

`mvn test` فشل بسبب Java 11 في البيئة التجريبية (المشروع يتطلب Java 21). هذا ليس خللاً في الكود.

---

## الجزء الحادي عشر: ملاحظات على المسائل (Problems)

### 11.1 ⚠️ MEDIUM — createProblem بدون @Transactional

**الملف:** `contestServer/service/ProblemService.java`

`deleteProblem` مُزيَّن بـ `@Transactional` ويُعالج الـ cascade يدوياً بشكل صحيح. لكن `createProblem` و `updateProblem` بدون `@Transactional` — إذا فشل حفظ أحد الـ test cases بعد حفظ المسألة، ستبقى المسألة في قاعدة البيانات بدون test cases.

**الحل:** إضافة `@Transactional` على `createProblem` و `updateProblem`.

---

## الجزء الثاني عشر: ملخص الـ Findings حسب الأولوية

### 🔴 CRITICAL (يجب الإصلاح فوراً)

| # | المشكلة | الملف | التأثير |
|---|---------|-------|---------|
| C-1 | `fromPublicEntity` يُرجع `expectedOutput` للفرق | `TestCaseResponse.java` | كسر أمان المسابقة بالكامل |

### 🟠 HIGH (يجب الإصلاح قبل الإطلاق)

| # | المشكلة | الملف | التأثير |
|---|---------|-------|---------|
| H-1 | Language validation بعد حفظ Submission | `SubmissionConsumer.java` | Submissions عالقة في PENDING |
| H-2 | SecurityConfig مخالف للـ @PreAuthorize على SSE | `SecurityConfiguration.java` | SSE refresh flow مكسور |
| H-3 | @Valid مفقود على جميع DTOs الرئيسية | متعددة | Input غير صالح بدون رسالة خطأ |
| H-4 | Callback endpoint بدون حماية HMAC/IP | `CallbackHandler.java` | حقن نتائج تحكيم وهمية |

### 🟡 MEDIUM

| # | المشكلة | الملف |
|---|---------|-------|
| M-1 | updateContestDetails لا يُطلق SSE | `ContestService.java` |
| M-2 | SubmissionStream بدون initial snapshot | `SubmissionStreamController.java` |
| M-3 | تكرار Auth code في frontend | `api.ts` / `teamApi.ts` |
| M-4 | تكرار exception packages | `exception/` و `exceptions/` |
| M-5 | RuntimeException بدلاً من ApiException | `SubmissionService.java` |
| M-6 | createProblem بدون @Transactional | `ProblemService.java` |
| M-7 | ADMIN يمكنه التقديم | `SubmissionController.java` |
| M-8 | TypeScript error في resizable.tsx | `resizable.tsx:40` |

### 🔵 LOW / INFO

| # | الملاحظة |
|---|---------|
| L-1 | AdminBootstrapRunner يكتب كلمة المرور في ملف نصي |
| L-2 | غياب @Index على جدول Submission |
| L-3 | SubmissionJudgeResult يتراكم بدون cleanup |
| L-4 | RejudgeService يُحمّل كل الـ Submissions في الذاكرة |
| L-5 | Admin SubmissionsView يُعيد جلب كل البيانات عند كل SSE |
| L-6 | لا integration tests ولا frontend tests |

---

## الجزء الثالث عشر: ما هو صحيح وجيد التنفيذ ✅

- **بنية SSE المشتركة:** جميع الـ registries تمتد من `SseEmitterRegistry` — بنية نظيفة بلا تكرار
- **judgeRunId safety:** Pre-reservation + stale callback check صحيح تماماً
- **Contest state machine:** `effectiveState` vs `persistedStatus` + `statusLocked` مُنفَّذ بدقة
- **Admin paused visibility:** `snapshot.active ?? snapshot.paused ?? snapshot.upcoming` ✅
- **Row-level locking:** `PESSIMISTIC_WRITE` في المواضع الصحيحة
- **@TransactionalEventListener(AFTER_COMMIT):** يمنع SSE قبل اكتمال الـ transaction
- **Exact-time scheduling:** `ContestTransitionScheduler` بالميلي ثانية
- **Heartbeat يصل لجميع الـ registries:** عبر Spring autowiring تلقائي
- **Frontend watchdog + exponential backoff:** منطق الاتصال في الـ hooks صحيح
- **Team stream endpoint isolation:** `/api/team/stream` للفرق، `/api/contest/stream` للـ admin

---

## الجزء الرابع عشر: نتيجة Build/Test

| الأمر | النتيجة | السبب |
|-------|---------|-------|
| `mvn test` | ❌ فشل | Java 11 في البيئة، المشروع يتطلب Java 21 — ليس خللاً في الكود |
| `npm run build` | ❌ فشل | `@rollup/rollup-linux-x64-gnu` مفقود (node_modules بُنيت على Windows) + TypeScript error في resizable.tsx |
| `npx tsc --noEmit` | ⚠️ خطأ واحد | `resizable.tsx:40` — `Property 'ref' does not exist on type 'PanelResizeHandleProps'` |

---

## الجزء الخامس عشر: التوصيات النهائية

### الأولوية الأولى — قبل أي تشغيل في بيئة إنتاج:
1. **إصلاح C-1:** تغيير `entity.getExpectedOutput()` إلى `null` في `fromPublicEntity`
2. **إصلاح H-1:** نقل language validation إلى `SubmissionService` قبل الحفظ
3. **إصلاح H-2:** مطابقة SecurityConfig مع @PreAuthorize للـ SSE endpoints
4. **إصلاح H-4:** إضافة HMAC verification على Judge0 callback endpoint

### الأولوية الثانية — قريباً:
5. إضافة `@Valid` + validation annotations على جميع DTOs
6. إضافة SSE event في `updateContestDetails`
7. دمج مجلدي `exception` و `exceptions`
8. إصلاح TypeScript error في `resizable.tsx`

### الأولوية الثالثة — تحسينات:
9. استخراج Auth logic المشترك في frontend
10. إضافة @Index على جدول Submission
11. إضافة integration tests للـ controllers
12. إضافة initial snapshot لـ SubmissionStream

---

*انتهى التقرير — AuraC2 Full System Audit — 2026-05-12*
