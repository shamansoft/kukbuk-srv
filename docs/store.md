Epic: Recipe Storage System
Goal: Reduce AI processing costs and improve response times by storing previously processed recipes

Phase 1: Core Storage Infrastructure
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

Same URLs produce identical hashes regardless of source format
Different recipe URLs produce different hashes
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
As a developer, I want to use Testcontainers during development, so I don't need GCP services running locally.
Acceptance Criteria:

Same repository works in local and production environments
Easy to clear/reset store during testing
No external dependencies beyond Docker and Testcontainers
Store data visible for debugging

Technical Tasks:

Set up Google Cloud module with Testcontainers
Create profile-based configuration (local vs production)
Add development scripts for store management
Configure emulator UI for store inspection
Document local development setup

Definition of Done:

When the app is started locally, a testcontainer with Firestore emulator is started.
Store works identically in local and production modes
[Optional] Developers can inspect stored recipes via REST API
Local storage can be easily reset for testing


Phase 2: Store Integration
KS-17.4: Recipe Processing with Store Check
As a user, I want my recipe requests to complete faster when the same content has been processed before.
Acceptance Criteria:

Check store before calling Gemini API
Store hit returns result in <500ms
Store miss processes normally and stores result
Response indicates whether result came from store (for debugging)
No functional difference between stored and fresh results

Technical Tasks:

Integrate store check into CookbookController.createRecipe()
Add store hit/miss logging and metrics
Store result in storage after successful Gemini processing
Add response header indicating store status
Handle store service failures gracefully

Definition of Done:

Store hits skip Gemini API calls completely
Store misses store results for future use
Error in store service doesn't break recipe processing
Performance improvement is measurable


KS-17.5: Store Analytics and Monitoring
As a product owner, I want to understand store performance so I can optimize the system and measure cost savings.
Acceptance Criteria:

Track store hit rate percentage
Monitor store usage and growth
Count Gemini API calls saved
Measure response time improvements
Alert on store service failures

Technical Tasks:

Add Micrometer metrics for store operations
Create custom metrics: hit_rate, storage_size, api_calls_saved
Set up GCP monitoring dashboards
Configure alerts for low hit rates or store failures
Add store statistics endpoint for debugging

Definition of Done:

Store metrics visible in GCP monitoring
Hit rate tracking shows improvement over time
Cost savings from reduced Gemini calls are measurable
Alerts trigger appropriately for store issues


Phase 3: Store Optimization
KS-17.6: Smart Store Key Strategy
As a system, I want to maximize store hits by recognizing similar content variations.
Acceptance Criteria:

Recognize recipe variations (different formatting, same content)
Handle mobile vs desktop versions of same recipe
Store recipe components separately (ingredients, instructions)
Fuzzy matching for near-duplicate content
Maintain store accuracy (no false positives)

Technical Tasks:

Implement content similarity scoring
Extract recipe-specific content for hashing
Add fuzzy matching algorithms
Create store key hierarchies (exact -> similar -> components)
Add validation to prevent incorrect store hits

Definition of Done:

Similar recipes with minor differences share store entries
No false positives (different recipes getting same store)
Store hit rate improves by 15-25% over exact matching
Performance impact of similarity checking is minimal


KS-17.7: Store Warming and Preloading
As a system, I want to proactively store popular recipes so users get faster responses.
Acceptance Criteria:

Identify popular recipe sources and URLs
Batch process popular recipes during low-traffic periods
Prioritize store warming based on usage patterns
Respect rate limits and API quotas
Track effectiveness of preloading

Technical Tasks:

Create background job for store warming
Implement recipe popularity scoring
Add batch processing with rate limiting
Schedule warming jobs during off-peak hours
Monitor warming job performance and effectiveness

Definition of Done:

Popular recipes are pre-stored before user requests
Store warming doesn't impact regular user requests
Warming effectiveness measured and optimized
System respects API rate limits during warming


KS-17.8: Store Invalidation and Updates
As a system, I want to handle recipe updates and ensure store freshness.
Acceptance Criteria:

Detect when source recipes have been updated
Provide manual store invalidation for specific URLs
Handle store corruption and validation
Update store with improved recipe processing
Maintain store consistency during updates

Technical Tasks:

Add store validation and integrity checks
Create admin endpoints for store management
Implement selective store invalidation
Add store versioning for schema changes
Handle concurrent store updates safely

Definition of Done:

Stale store entries are detected and refreshed
Manual store invalidation works correctly
Store corruption is detected and resolved automatically
System handles store schema migrations


Phase 4: Advanced Features
KS-17.9: Intelligent Store Partitioning
As a system, I want to optimize store usage and performance based on usage patterns.
Acceptance Criteria:

Separate hot and cold store tiers
Compress rarely accessed store entries
Implement store eviction policies (LRU, frequency-based)
Optimize storage costs vs access speed
Monitor and tune partitioning effectiveness

Technical Tasks:

Implement multi-tier store architecture
Add store entry compression for cold storage
Create smart eviction policies
Monitor access patterns and optimize accordingly
Add store tier migration logic


KS-17.10: Cross-User Store Sharing
As a system, I want to share store entries across users for maximum efficiency.
Acceptance Criteria:

Same recipe stored once, used by all users
Respect user privacy and data isolation
Handle user-specific customizations
Maintain store security and access controls
Track shared store usage and benefits

Technical Tasks:

Design shared store architecture
Implement privacy-preserving store keys
Add user-specific store overlays
Secure shared store access
Measure cross-user store effectiveness


Success Metrics
Performance Metrics

Store Hit Rate: Target 60-80% after 3 months
Response Time: 70% reduction for stored recipes
API Cost Reduction: 60-80% reduction in Gemini API calls
Store Growth: <1GB per 10K unique recipes

Quality Metrics

Store Accuracy: <0.1% false positive rate
User Satisfaction: No degradation in recipe quality
System Reliability: 99.9% store service uptime

Business Metrics

Cost Savings: Measure monthly Gemini API cost reduction
User Experience: Faster recipe processing improves retention
Scalability: System handles 10x traffic with same response times