# YouTube Recipe Extraction - Schema Analysis

## Goal 2: Additional YouTube API Data That Would Be Helpful

### Currently Available Data
- ✓ Video title
- ✓ Video description
- ✓ Transcript/captions
- ✓ Channel ID
- ✓ Video ID
- ✓ URL

### Recommended Additional YouTube API Fields

#### High Priority (Very Useful)

1. **Video Duration** (`snippet.duration` from API)
   - **Why**: Validate estimated cook time, provide user context
   - **Usage**: Cross-check `total_time` estimates from transcript
   - **Example**: "PT15M30S" → 15 minutes 30 seconds
   - **Schema mapping**: Can validate `metadata.total_time`

2. **Published Date** (`snippet.publishedAt`)
   - **Why**: Better than using "current date" for `metadata.date_created`
   - **Usage**: Set actual publication date of recipe
   - **Example**: "2024-05-08T..." → "2024-05-08"
   - **Schema mapping**: `metadata.date_created`

3. **Channel Title** (`snippet.channelTitle`)
   - **Why**: Recipe author attribution
   - **Usage**: Set recipe author to channel name
   - **Example**: "Kitchen with Accent" → `metadata.author`
   - **Schema mapping**: `metadata.author`

4. **Thumbnail URLs** (`snippet.thumbnails.high.url` or `maxres`)
   - **Why**: High-quality cover image for recipe
   - **Usage**: Recipe preview image
   - **Example**: `https://i.ytimg.com/vi/DfddXdM-xds/maxresdefault.jpg`
   - **Schema mapping**: `metadata.cover_image.path`

5. **View Count** (`statistics.viewCount`)
   - **Why**: Popularity indicator, quality signal
   - **Usage**: Could be used for ranking/sorting recipes
   - **Example**: "186579" views
   - **Schema extension needed**: `metadata.popularity` or `metadata.view_count`

6. **Video Tags** (`snippet.tags`)
   - **Why**: Additional metadata for categorization
   - **Usage**: Supplement `metadata.tags` field
   - **Example**: ["adjika", "Georgian food", "spicy sauce", "condiment"]
   - **Schema mapping**: `metadata.tags` (merge with extracted tags)

7. **Video Category** (`snippet.categoryId`)
   - **Why**: YouTube category (e.g., "26" = Howto & Style)
   - **Usage**: Validate it's actually a cooking video
   - **Example**: Filter out non-cooking videos
   - **Schema mapping**: Could validate `is_recipe` flag

#### Medium Priority (Nice to Have)

8. **Default Audio Language** (`snippet.defaultAudioLanguage`)
   - **Why**: More reliable than transcript detection
   - **Usage**: Set `metadata.language` directly
   - **Example**: "ru" for Russian video
   - **Schema mapping**: `metadata.language`

9. **Caption Track Languages** (`captions.list`)
   - **Why**: Multi-language recipe support
   - **Usage**: Extract recipe in multiple languages if available
   - **Example**: Get English captions if available for Russian video
   - **Schema extension needed**: `metadata.available_languages: ["ru", "en"]`

10. **Like/Dislike Ratio** (`statistics.likeCount`)
    - **Why**: Quality signal
    - **Usage**: Filter low-quality recipes
    - **Example**: High likes → likely good recipe
    - **Schema extension needed**: `metadata.rating` or `metadata.likes`

11. **Comment Count** (`statistics.commentCount`)
    - **Why**: Engagement indicator
    - **Usage**: Could scrape top comments for tips/variations
    - **Example**: Parse comments for "I added X instead" variations
    - **Schema mapping**: Could populate `notes` or `substitutions`

12. **Video Definition** (`contentDetails.definition`)
    - **Why**: Quality indicator (HD vs SD)
    - **Usage**: Preference for HD videos (better visual quality)
    - **Example**: "hd" vs "sd"
    - **Not in schema**: Internal quality filter

#### Low Priority (Edge Cases)

13. **Caption Type** (`captions.snippet.trackKind`)
    - **Why**: Distinguish auto-generated vs manual captions
    - **Usage**: Manual captions are more accurate
    - **Example**: `asr` (auto) vs `standard` (manual)
    - **Not in schema**: Internal processing hint

14. **Video Chapters** (parsed from description timestamps)
    - **Why**: Pre-segmented video sections
    - **Usage**: Map to instruction steps automatically
    - **Example**: "00:36 - mix spices, 02:07 - grind pepper"
    - **Schema mapping**: Map to `instructions[].media[].duration`

15. **Related Video IDs** (`relatedToVideoId` from search)
    - **Why**: Find recipe variations
    - **Usage**: Suggest related recipes
    - **Not in schema**: External recommendation system

### YouTube API Data Retrieval Strategy

```javascript
// Primary API call
const videoResponse = await youtube.videos.list({
  part: ['snippet', 'contentDetails', 'statistics'],
  id: videoId
});

// Extract fields
const {
  snippet: {
    title,
    description,
    channelId,
    channelTitle,
    publishedAt,
    thumbnails,
    tags,
    defaultAudioLanguage,
    categoryId
  },
  contentDetails: {
    duration,
    definition
  },
  statistics: {
    viewCount,
    likeCount,
    commentCount
  }
} = videoResponse.data.items[0];

// Secondary call for captions
const captionsResponse = await youtube.captions.list({
  part: ['snippet'],
  videoId: videoId
});

// Download specific caption track
const transcriptResponse = await youtube.captions.download({
  id: captionId,
  tfmt: 'srt' // or 'vtt'
});
```

---

## Goal 3: Schema Evaluation and Extension Recommendations

### Current Schema Compatibility Analysis

#### ✅ Works Well For Web/HTML Sources
- `metadata.source` (URL) ✓
- `metadata.author` (from article byline) ✓
- `metadata.date_created` (from article metadata) ✓
- `metadata.cover_image` (from og:image) ✓
- All ingredient/instruction fields ✓

#### ⚠️ Partially Works For YouTube
- `metadata.source` → YouTube URL ✓
- `metadata.author` → Channel name ✓ (but needs channel_id too)
- `metadata.date_created` → Video publish date ✓
- `metadata.cover_image.path` → Thumbnail URL ✓
- **ISSUE**: No place for video-specific metadata (video_id, channel_id, duration)

#### ❌ Problematic For Photos/OCR/Scans
- `metadata.source` → What to use? Local file path? Upload URL? ⚠️
- `metadata.author` → Not applicable for scanned recipes ❌
- `metadata.date_created` → OCR processing date? Original document date? ⚠️
- **ISSUE**: No way to indicate source type
- **ISSUE**: No way to store OCR confidence scores
- **ISSUE**: No way to reference original image

### Recommended Schema Extensions

#### Extension 1: Add `source_metadata` Object

```json
{
  "metadata": {
    "title": "...",
    "source": "https://youtube.com/watch?v=DfddXdM-xds",
    "source_metadata": {
      "type": "youtube",
      "youtube": {
        "video_id": "DfddXdM-xds",
        "channel_id": "UCnSattLOoumy75cbseuL8AA",
        "channel_name": "Kitchen with Accent",
        "published_at": "2024-05-08T10:00:00Z",
        "duration": "PT8M15S",
        "view_count": 186579,
        "like_count": 5234,
        "thumbnail_url": "https://i.ytimg.com/vi/DfddXdM-xds/maxresdefault.jpg"
      }
    }
  }
}
```

**Why**: Preserves platform-specific metadata without polluting core schema

#### Extension 2: Add `source_type` Enum

```json
{
  "metadata": {
    "source_type": "youtube" // enum: ["web", "youtube", "photo", "ocr", "scan", "manual"]
  }
}
```

**Why**: Explicit indication of recipe origin

#### Extension 3: Add `ocr_metadata` for Photos/Scans

```json
{
  "metadata": {
    "source": "file://recipe-card.jpg",
    "source_type": "photo",
    "source_metadata": {
      "type": "ocr",
      "ocr": {
        "confidence": 0.95,
        "language_detected": "en",
        "image_path": "/uploads/recipe-card.jpg",
        "image_hash": "sha256:abc123...",
        "processed_at": "2026-02-06T10:30:00Z",
        "ocr_engine": "Google Cloud Vision API v2"
      }
    }
  }
}
```

**Why**: Track OCR quality and original source

#### Extension 4: Add `media_references` Array

For recipes extracted from video, keep original video reference:

```json
{
  "media_references": [
    {
      "type": "video",
      "platform": "youtube",
      "url": "https://youtube.com/watch?v=DfddXdM-xds",
      "video_id": "DfddXdM-xds",
      "thumbnail": "https://i.ytimg.com/vi/DfddXdM-xds/maxresdefault.jpg",
      "duration": "PT8M15S"
    }
  ]
}
```

**Why**: Preserve link to original video for user reference

#### Extension 5: Enhanced `cover_image` for Multiple Sources

```json
{
  "metadata": {
    "cover_image": {
      "path": "https://i.ytimg.com/vi/DfddXdM-xds/maxresdefault.jpg",
      "alt": "Georgian Adjika in a bowl",
      "source": "youtube_thumbnail",
      "width": 1280,
      "height": 720
    }
  }
}
```

**Why**: Indicate image source and quality

### Proposed Schema v1.1.0 Changes

#### Backward Compatible Additions

```json
{
  "metadata": {
    // Existing fields...
    "source_type": {
      "type": "string",
      "enum": ["web", "youtube", "photo", "ocr", "scan", "manual"],
      "description": "Type of source the recipe was extracted from"
    },
    "source_metadata": {
      "type": "object",
      "description": "Platform-specific metadata",
      "properties": {
        "type": {"type": "string"},
        "youtube": {
          "type": "object",
          "properties": {
            "video_id": {"type": "string"},
            "channel_id": {"type": "string"},
            "channel_name": {"type": "string"},
            "published_at": {"type": "string", "format": "date-time"},
            "duration": {"type": "string", "pattern": "^PT(\\d+H)?(\\d+M)?(\\d+S)?$"},
            "view_count": {"type": "integer"},
            "like_count": {"type": "integer"},
            "thumbnail_url": {"type": "string", "format": "uri"}
          }
        },
        "ocr": {
          "type": "object",
          "properties": {
            "confidence": {"type": "number", "minimum": 0, "maximum": 1},
            "language_detected": {"type": "string"},
            "image_path": {"type": "string"},
            "image_hash": {"type": "string"},
            "processed_at": {"type": "string", "format": "date-time"},
            "ocr_engine": {"type": "string"}
          }
        }
      }
    }
  }
}
```

### Comparison: Schema Fitness for Different Sources

| Feature | Web/HTML | YouTube | Photo/OCR | Scan |
|---------|----------|---------|-----------|------|
| `metadata.title` | ✅ Excellent | ✅ Excellent | ⚠️ May need OCR | ⚠️ May need OCR |
| `metadata.source` (URL) | ✅ Perfect | ✅ Works (YouTube URL) | ❌ No URL (use file path) | ❌ No URL |
| `metadata.author` | ✅ From byline | ✅ From channel | ❌ Often unknown | ⚠️ May be printed |
| `metadata.date_created` | ✅ Article date | ✅ Publish date | ⚠️ OCR date? | ⚠️ Unknown |
| `metadata.language` | ✅ Detect from HTML | ✅ Detect from transcript | ✅ OCR detect | ✅ OCR detect |
| `ingredients` | ✅ Structured lists | ✅ From description/transcript | ⚠️ OCR may have errors | ⚠️ OCR may have errors |
| `instructions` | ✅ Structured lists | ✅ From transcript | ⚠️ OCR quality-dependent | ⚠️ OCR quality-dependent |
| `cover_image` | ✅ From article | ✅ Thumbnail | ✅ Source image itself | ✅ Source scan itself |
| **Platform metadata** | ❌ No native support | ❌ No native support | ❌ No native support | ❌ No native support |
| **Source type indicator** | ❌ Missing | ❌ Missing | ❌ Missing | ❌ Missing |
| **Quality metadata** | N/A | ⚠️ No view count | ❌ No confidence score | ❌ No confidence score |

### Recommendations

#### Short Term (For YouTube Support)
1. **Use existing schema** - It works adequately
2. **Store YouTube metadata in `notes` field** as structured Markdown:
   ```markdown
   **Video Information:**
   - Channel: Kitchen with Accent
   - Video ID: DfddXdM-xds
   - Views: 186,579
   - Published: May 8, 2024
   ```
3. **Use `metadata.cover_image.path`** for YouTube thumbnail
4. **Use video URL in `metadata.source`**

#### Medium Term (Schema v1.1.0)
1. **Add `source_type` enum field**
2. **Add `source_metadata` object** with platform-specific data
3. **Enhance `cover_image`** with source indicator
4. **Update validation** to handle multiple source types

#### Long Term (Schema v2.0.0)
1. **Full multi-source support** with dedicated schemas per source type
2. **OCR confidence tracking** throughout
3. **Media library integration** for photos/scans
4. **Recipe versioning** from OCR improvements over time

---

## Implementation Checklist for YouTube Support

### Phase 1: Prompt Creation ✅
- [x] Create YouTube-specific extraction prompt
- [x] Handle transcript-specific issues (repetitions, failed takes, informal language)
- [x] Prioritize description for ingredients
- [x] Use video timestamps for instruction organization

### Phase 2: API Integration (TODO)
- [ ] Add YouTube API client
- [ ] Fetch video metadata (title, description, thumbnails, etc.)
- [ ] Download captions/transcript
- [ ] Parse video chapters from description

### Phase 3: Data Mapping (TODO)
- [ ] Map YouTube URL → `metadata.source`
- [ ] Map channel name → `metadata.author`
- [ ] Map published date → `metadata.date_created`
- [ ] Map thumbnail → `metadata.cover_image.path`
- [ ] Store video_id, channel_id in `notes` (temporary solution)

### Phase 4: Schema Extension (Future)
- [ ] Propose schema v1.1.0 with `source_metadata`
- [ ] Update validation to support YouTube-specific fields
- [ ] Migrate existing recipes to new schema
- [ ] Update documentation

### Phase 5: OCR Support (Future)
- [ ] Research OCR engines (Google Vision, AWS Textract, Tesseract)
- [ ] Create OCR-specific prompt
- [ ] Handle confidence scores and error correction
- [ ] Design schema for photo/scan sources
