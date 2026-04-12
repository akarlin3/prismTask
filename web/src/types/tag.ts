export interface Tag {
  id: number;
  user_id: number;
  name: string;
  color: string | null;
  created_at: string;
}

export interface TagCreate {
  name: string;
  color?: string;
}

export interface TagUpdate {
  name?: string;
  color?: string;
}
