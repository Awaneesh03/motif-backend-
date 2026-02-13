# Railway Runtime Crash - Diagnostic Checklist

## Most Common Causes (in order of likelihood):

1. **Database Connection Failure** ⚠️ CRITICAL
   - Supabase env vars missing/incorrect
   - Spring Boot crashes if DB unreachable at startup
   - Solution: Make DB connection lazy/optional

2. **JPA/Hibernate Startup Failure**
   - Tries to validate schema on startup
   - Fails if DB connection fails
   - Solution: Disable DDL validation

3. **Missing Environment Variables**
   - Required: SUPABASE_DB_URL, SUPABASE_DB_USER, SUPABASE_DB_PASSWORD
   - Required: SUPABASE_URL, SUPABASE_JWT_SECRET, SUPABASE_ANON_KEY
   - Required: GROQ_API_KEY
   - Solution: Check Railway env vars

4. **Health Check Failure**
   - DB health check enabled but DB unavailable
   - Railway kills service if health check fails
   - Solution: Make health checks non-blocking

5. **Redis Connection** (Already fixed - disabled)

## Immediate Fixes Needed:
