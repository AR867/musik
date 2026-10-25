# Music Form Security Specification (Phase 0 TDD)

## 1. Data Invariants
1. **Strict Multi-Tenant Isolation**: Every document under `/users/{userId}`, `/users/{userId}/playlists/{playlistId}`, and `/users/{userId}/interactions/{interactionId}` can only be read, listed, created, updated, or deleted when `request.auth != null && request.auth.uid == userId`.
2. **Identity Integrity**: Every document payload must include `userId == request.auth.uid` and `userId == userId` (path variable), and `userId` + `createdAt` are immutable across updates.
3. **Bounded Collections & Strings**: Every string field has strict `.size()` bounds; array fields (`preferredGenres`, `excludedArtists`, `trackIds`) have strict maximum lengths (`20`, `50`, `200`).
4. **Temporal Integrity**: `createdAt` and `updatedAt` must be valid Firestore `timestamp` values `<= request.time`.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Profile Read**: `unauthDb.collection("users").doc("alice").get()` -> Reject
2. **Cross-User Profile Read (PII Isolation)**: `bobDb.collection("users").doc("alice").get()` -> Reject
3. **Shadow Field Injection on Profile**: Adding `isAdmin: true` to `/users/{userId}` -> Reject via `hasOnly`
4. **Invalid Subscription Enum**: Setting `subscriptionTier: "ULTRA_HACK"` -> Reject via `in ['FREE', 'PREMIUM']`
5. **Identity Spoofing on Playlist Create**: `aliceDb` writing `userId: "bob"` inside `/users/alice/playlists/p1` -> Reject
6. **Cross-User Playlist List**: `bobDb.collection("users").doc("alice").collection("playlists").get()` -> Reject
7. **Unbounded TrackIds Array**: Playlist with > 200 items -> Reject
8. **Immutable Field Mutation**: Updating `createdAt` or `userId` on a playlist -> Reject
9. **Value Poisoning on Update**: Updating `title` on a playlist to an integer `12345` or empty string `""` -> Reject via `isValidUserPlaylist(incoming())`
10. **Negative PlayCount / ListenMs**: Creating `TrackInteraction` with `playCount: -5` -> Reject
11. **Cross-User Interaction Write**: `bobDb` writing to `/users/alice/interactions/t1` -> Reject
12. **Future Timestamp Spoofing**: Writing `createdAt` in the future (`> request.time`) -> Reject
