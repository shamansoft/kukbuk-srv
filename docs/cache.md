Epic: Recipe Caching System
Goal: Reduce AI processing costs and improve response times by caching previously processed recipes

Phase 1: Core Caching Infrastructure
KS-17.1: Content Hash Generation
As a system, I want to generate unique fingerprints for recipe content so I can identify duplicate processing requests.
Acceptance Criteria:

Generate SHA-256 hash from normalized URL
Normalize URLs by removing tracking parameters
Hash generation completes in <50ms for typical recipe pages

Technical Tasks:

Implement URL normalization (remove utm_*, fbclid, etc.)
Write unit tests for hash consistency across URLs
Add performance benchmarks for hash generation

Definition of Done:

Same URLs produces identical hashes regardless of source format
Different recipes URLs produce different hashes
Hash generation is deterministic and fast
Unit tests cover edge cases (malformed HTML, missing content)


KS-17.2: Storage Infrastructure
As a system, I want to store and retrieve processed recipes so I can avoid redundant AI processing.
Acceptance Criteria:

Store recipe YAML with content hash as key
Include metadata: source URL, creation timestamp, access count
Retrieve stored recipes by content hash in <100ms
Handle store misses gracefully without errors

Technical Tasks:

Storage - Google's Firestore for production, Testcontainers google-cloud module for local dev and integration tests
Create Repository interface with get/put operations
Implement FirestoreRepository for production
Create a model with required fields
Configure Firestore indexes for performance (if needed)

Definition of Done:

Store operations complete within performance thresholds
Store survives application restarts
Store operations don't block recipe processing on failures

KS-17.3: Local Development Store
As a developer, I want to use Testcontainers during development so I don't need GCP services running locally.
Acceptance Criteria:

Same repository works in local and production environments
Easy to clear/reset store during testing
No external dependencies beyond Docker and Testcontainers
Store data visible for debugging

Technical Tasks:

Set up Google Cloud module with Testcontainers
Create profile-based configuration (local vs production)
Add development scripts for cache management
Configure emulator UI for cache inspection
Document local development setup

Definition of Done:

When the app is started locally, a testcontainer with firestore emulator is started.
Store works identically in local and production modes
[Optional] Developers can inspect stored recipes via REST api
Local storage can be easily reset for testing


Phase 2: Cache Integration
KS-17.4: Recipe Processing with Cache Check
As a user, I want my recipe requests to complete faster when the same content has been processed before.
Acceptance Criteria:

Check cache before calling Gemini API
Cache hit returns result in <500ms
Cache miss processes normally and stores result
Response indicates whether result came from cache (for debugging)
No functional difference between cached and fresh results

Technical Tasks:

Integrate cache check into CookbookController.createRecipe()
Add cache hit/miss logging and metrics
Store result in cache after successful Gemini processing
Add response header indicating cache status
Handle cache service failures gracefully

Definition of Done:

Cache hits skip Gemini API calls completely
Cache misses store results for future use
Error in cache service doesn't break recipe processing
Performance improvement is measurable


KS-17.5: Cache Analytics and Monitoring
As a product owner, I want to understand cache performance so I can optimize the system and measure cost savings.
Acceptance Criteria:

Track cache hit rate percentage
Monitor cache storage usage and growth
Count Gemini API calls saved
Measure response time improvements
Alert on cache service failures

Technical Tasks:

Add Micrometer metrics for cache operations
Create custom metrics: hit_rate, storage_size, api_calls_saved
Set up GCP monitoring dashboards
Configure alerts for low hit rates or cache failures
Add cache statistics endpoint for debugging

Definition of Done:

Cache metrics visible in GCP monitoring
Hit rate tracking shows improvement over time
Cost savings from reduced Gemini calls are measurable
Alerts trigger appropriately for cache issues


Phase 3: Cache Optimization
KS-17.6: Smart Cache Key Strategy
As a system, I want to maximize cache hits by recognizing similar content variations.
Acceptance Criteria:

Recognize recipe variations (different formatting, same content)
Handle mobile vs desktop versions of same recipe
Cache recipe components separately (ingredients, instructions)
Fuzzy matching for near-duplicate content
Maintain cache accuracy (no false positives)

Technical Tasks:

Implement content similarity scoring
Extract recipe-specific content for hashing
Add fuzzy matching algorithms
Create cache key hierarchies (exact -> similar -> components)
Add validation to prevent incorrect cache hits

Definition of Done:

Similar recipes with minor differences share cache entries
No false positives (different recipes getting same cache)
Cache hit rate improves by 15-25% over exact matching
Performance impact of similarity checking is minimal


KS-17.7: Cache Warming and Preloading
As a system, I want to proactively cache popular recipes so users get faster responses.
Acceptance Criteria:

Identify popular recipe sources and URLs
Batch process popular recipes during low-traffic periods
Prioritize cache warming based on usage patterns
Respect rate limits and API quotas
Track effectiveness of preloading

Technical Tasks:

Create background job for cache warming
Implement recipe popularity scoring
Add batch processing with rate limiting
Schedule warming jobs during off-peak hours
Monitor warming job performance and effectiveness

Definition of Done:

Popular recipes are pre-cached before user requests
Cache warming doesn't impact regular user requests
Warming effectiveness measured and optimized
System respects API rate limits during warming


KS-17.8: Cache Invalidation and Updates
As a system, I want to handle recipe updates and ensure cache freshness.
Acceptance Criteria:

Detect when source recipes have been updated
Provide manual cache invalidation for specific URLs
Handle cache corruption and validation
Update cache with improved recipe processing
Maintain cache consistency during updates

Technical Tasks:

Add cache validation and integrity checks
Create admin endpoints for cache management
Implement selective cache invalidation
Add cache versioning for schema changes
Handle concurrent cache updates safely

Definition of Done:

Stale cache entries are detected and refreshed
Manual cache invalidation works correctly
Cache corruption is detected and resolved automatically
System handles cache schema migrations


Phase 4: Advanced Features
KS-17.9: Intelligent Cache Partitioning
As a system, I want to optimize cache storage and performance based on usage patterns.
Acceptance Criteria:

Separate hot and cold cache tiers
Compress rarely accessed cache entries
Implement cache eviction policies (LRU, frequency-based)
Optimize storage costs vs access speed
Monitor and tune partitioning effectiveness

Technical Tasks:

Implement multi-tier cache architecture
Add cache entry compression for cold storage
Create smart eviction policies
Monitor access patterns and optimize accordingly
Add cache tier migration logic


KS-17.10: Cross-User Cache Sharing
As a system, I want to share cache entries across users for maximum efficiency.
Acceptance Criteria:

Same recipe cached once, used by all users
Respect user privacy and data isolation
Handle user-specific customizations
Maintain cache security and access controls
Track shared cache usage and benefits

Technical Tasks:

Design shared cache architecture
Implement privacy-preserving cache keys
Add user-specific cache overlays
Secure shared cache access
Measure cross-user cache effectiveness


Success Metrics
Performance Metrics

Cache Hit Rate: Target 60-80% after 3 months
Response Time: 70% reduction for cached recipes
API Cost Reduction: 60-80% reduction in Gemini API calls
Cache Storage Growth: <1GB per 10K unique recipes

Quality Metrics

Cache Accuracy: <0.1% false positive rate
User Satisfaction: No degradation in recipe quality
System Reliability: 99.9% cache service uptime

Business Metrics

Cost Savings: Measure monthly Gemini API cost reduction
User Experience: Faster recipe processing improves retention
Scalability: System handles 10x traffic with same response times