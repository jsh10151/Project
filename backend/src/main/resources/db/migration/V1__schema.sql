CREATE TABLE members (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 email VARCHAR(255) NOT NULL UNIQUE,
 password_hash VARCHAR(255) NOT NULL,
 name VARCHAR(50) NOT NULL,
 role VARCHAR(10) NOT NULL DEFAULT 'USER',
 CHECK(role IN ('USER','ADMIN'))
);
CREATE TABLE spaces (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(100) NOT NULL,
 district VARCHAR(50) NOT NULL,
 address VARCHAR(200) NOT NULL,
 kind VARCHAR(30) NOT NULL,
 capacity INT NOT NULL,
 hourly_price INT NOT NULL,
 image_url VARCHAR(1000) NOT NULL,
 description TEXT NOT NULL,
 amenities TEXT NOT NULL,
 rules TEXT NOT NULL,
 open_hour INT NOT NULL DEFAULT 9,
 close_hour INT NOT NULL DEFAULT 22,
 CHECK(capacity BETWEEN 1 AND 100),
 CHECK(hourly_price BETWEEN 1000 AND 1000000),
 CHECK(open_hour >= 0 AND close_hour <= 24 AND close_hour > open_hour)
);
CREATE TABLE bookings (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 member_id BIGINT NOT NULL,
 space_id BIGINT NOT NULL,
 starts_at DATETIME(6) NOT NULL,
 ends_at DATETIME(6) NOT NULL,
 guests INT NOT NULL,
 total_price INT NOT NULL,
 status VARCHAR(15) NOT NULL DEFAULT 'CONFIRMED',
 request_key VARCHAR(64) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(member_id) REFERENCES members(id),
 FOREIGN KEY(space_id) REFERENCES spaces(id),
 UNIQUE(member_id,request_key),
 INDEX idx_booking_overlap(space_id,status,starts_at,ends_at),
 INDEX idx_booking_member(member_id,created_at),
 CHECK(ends_at > starts_at),
 CHECK(guests > 0 AND total_price > 0),
 CHECK(status IN ('CONFIRMED','CANCELLED'))
);
INSERT INTO spaces(name,district,address,kind,capacity,hourly_price,image_url,description,amenities,rules) VALUES
('오후의 아틀리에','성수','서울 성동구 연무장길 18','촬영',8,35000,'https://images.unsplash.com/photo-1600210492486-724fe5c67fb0?auto=format&fit=crop&w=1600&q=85','햇빛이 천천히 머무는 공간. 따뜻한 우드와 편안한 가구가 일상의 장면을 특별하게 만들어 줍니다.','자연광,와이파이,냉난방,블루투스 스피커','실내 금연 · 이용 시간을 지켜주세요 · 가구 이동 후 원상 복구'),
('테이블 앤 토크','연남','서울 마포구 동교로 240','모임',12,25000,'https://images.unsplash.com/photo-1497366754035-f200968a6e72?auto=format&fit=crop&w=1600&q=85','좋은 대화가 시작되는 큰 테이블. 작은 워크숍과 함께하는 저녁에 잘 어울립니다.','대형 테이블,와이파이,빔프로젝터,화이트보드','실내 금연 · 음식물 정리 필수 · 반려동물 동반 불가'),
('화이트 프레임','을지로','서울 중구 을지로 156','전시',20,45000,'https://images.unsplash.com/photo-1577083552431-6e5fd01aa342?auto=format&fit=crop&w=1600&q=85','당신의 이야기를 담는 하얀 캔버스. 작은 전시와 브랜드의 첫 인사를 위한 공간입니다.','전시 레일,조명,와이파이,냉난방','벽면 타공 금지 · 작품 설치는 사전 협의 · 실내 금연'),
('슬로우 선데이','망원','서울 마포구 포은로 82','모임',6,20000,'https://images.unsplash.com/photo-1616486338812-3dadae4b4ace?auto=format&fit=crop&w=1600&q=85','바쁜 도시에서 만나는 느긋한 하루. 친구와의 모임부터 소규모 독서 모임까지.','소파,커피 도구,와이파이,스피커','밤 10시 이후 이용 불가 · 소음 주의 · 실내 금연'),
('스튜디오 모먼트','성수','서울 성동구 뚝섬로 88','촬영',10,40000,'https://images.unsplash.com/photo-1600607687920-4e2a09cf159d?auto=format&fit=crop&w=1600&q=85','감각적인 소재와 빛이 만드는 장면. 제품 촬영과 콘텐츠 제작을 위한 여유로운 스튜디오.','자연광,전신거울,탈의 공간,와이파이','장비는 직접 지참 · 촬영 인원 준수 · 실내 금연'),
('작은 취향의 방','한남','서울 용산구 이태원로 228','워크숍',8,30000,'https://images.unsplash.com/photo-1497366811353-6870744d04b2?auto=format&fit=crop&w=1600&q=85','영감이 필요한 날, 함께 만드는 시간. 창작 워크숍과 소규모 클래스에 맞춘 공간입니다.','작업 테이블,싱크대,와이파이,냉난방','재료는 직접 준비 · 사용 후 정리 · 위험물 반입 금지');
