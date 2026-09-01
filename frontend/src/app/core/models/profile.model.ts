// SOCIAL-05 (D-07): field names character-identical to backend/.../dto/ProfileResponse.java
// and UpdateInterestsRequest.java.
export interface Profile {
  id: number;
  username: string;
  joinedAt: string; // ISO instant
  interests: string[];
}

export interface UpdateInterestsRequest {
  interests: string[];
}
