CREATE TABLE IF NOT EXISTS users (
                                     id SERIAL PRIMARY KEY,
                                     username TEXT UNIQUE NOT NULL,
                                     password_hash TEXT NOT NULL,
                                     email TEXT,
                                     favorite_genre TEXT,
                                     created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

CREATE TABLE IF NOT EXISTS tokens (
                                      token TEXT PRIMARY KEY,
                                      user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NULL
    );

CREATE TABLE IF NOT EXISTS media (
                                     id SERIAL PRIMARY KEY,
                                     creator_user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT,
    media_type TEXT NOT NULL CHECK (media_type IN ('movie','series','game')),
    release_year INT CHECK (release_year BETWEEN 1900 AND EXTRACT(YEAR FROM now())::INT + 1),
    age_restriction INT CHECK (age_restriction IN (0,6,12,16,18)),
    genres TEXT[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

CREATE TABLE IF NOT EXISTS media_entries (
                                             id       SERIAL PRIMARY KEY,
                                             title    TEXT NOT NULL,
                                             rating   INT  NOT NULL CHECK (rating BETWEEN 0 AND 10),
    user_id  INT  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

-- Ratings Tabelle: 1 Rating pro User/Media (1-5 Sterne, editierbar)
-- Bezieht sich auf media_entries (nicht media), da diese Tabelle aktuell verwendet wird
CREATE TABLE IF NOT EXISTS ratings (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_id INT NOT NULL REFERENCES media_entries(id) ON DELETE CASCADE,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    likes INT NOT NULL DEFAULT 0,
    confirmed BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, media_id)  -- Ein User kann nur 1 Rating pro Media haben
);

-- Comments Tabelle: Kommentare zu Media mit Bestätigung vor Veröffentlichung
CREATE TABLE IF NOT EXISTS comments (
    id SERIAL PRIMARY KEY,
    media_id INT NOT NULL REFERENCES media_entries(id) ON DELETE CASCADE,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    text TEXT NOT NULL,
    approved BOOLEAN NOT NULL DEFAULT false,  -- Bestätigung vor Veröffentlichung
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Favorites Tabelle: Markierte Favoriten eines Users
CREATE TABLE IF NOT EXISTS favorites (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_id INT NOT NULL REFERENCES media_entries(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, media_id)  -- Ein User kann ein Media nur einmal als Favorite markieren
);

-- Rating Likes Tabelle: Welche User haben welche Ratings geliked
CREATE TABLE IF NOT EXISTS rating_likes (
    id SERIAL PRIMARY KEY,
    rating_id INT NOT NULL REFERENCES ratings(id) ON DELETE CASCADE,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(rating_id, user_id)  -- Ein User kann ein Rating nur einmal liken
);
