-- Users
-- - username
-- - pwd
-- - id
-- - // tokens: json { [ provider: [ client: token ] ] }
-- - bans: json
CREATE TABLE users (
    id serial primary key,
    username text not null,
    screen_name text,
    hashed_password text,
    email text,
    is_private smallint,
    type text,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    deleted_at timestamp null,
    profile_picture_uuid uuid null,
    profile text,
    tokens jsonb,
    bans jsonb
);


CREATE TABLE subscriptions (
    subscriber_id int not null references users(id) on delete cascade,
    subscription_id int not null references users(id) on delete cascade
);


CREATE TABLE blocks (
    blocker_id int not null references users(id) on delete cascade,
    target_id int not null references users(id) on delete cascade
);


CREATE TABLE group_admins (
    group_id int not null references users(id) on delete cascade,
    admin_id int not null references users(id) on delete cascade
);


CREATE TABLE subscription_requests (
    subscriber_id_id int not null references users(id) on delete cascade,
    target_id int not null references users(id) on delete cascade,
    created_at timestamp not null default now()
);


-- Posts
-- - body
-- - meta: { to: [ timeline_id ] } // unless select union
-- - timestamp
CREATE TABLE posts (
    id serial primary key,
    user_id int not null references users(id) on delete no action,
    body text,
    meta jsonb,
    created_at timestamp,
    updated_at timestamp,
    deleted_at timestamp
);


-- Likes
-- - timestamp
-- - user_id
CREATE TABLE likes (
    user_id int not null references users(id),
    post_id int not null references posts(id),
    created_at timestamp,
    deleted_at timestamp
);

-- Comments
-- - body
-- - timestamp
CREATE TABLE comments (
    id serial primary key,
    user_id int not null references users(id),
    post_id int not null references posts(id),
    body text,
    created_at timestamp,
    updated_at timestamp,
    deleted_at timestamp
);

