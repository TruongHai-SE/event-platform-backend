# Ad position pricing

## Mục tiêu và quy tắc giá

Một campaign có thể mua nhiều `position`. Mỗi position có một `bonus_price` cố định (VND), do Admin cấu hình. Khi Organizer đăng ký, hệ thống lấy giá gói và phụ phí của từng position đang active để tạo snapshot:

`total_price = package_price + SUM(campaign_positions.bonus_price)`

Ví dụ: gói `100000`, `HOME_TOP` `50000`, `HOME_MIDDLE` `30000` thì `total_price` là `180000`.

Các thay đổi bảng giá position hoặc giá gói sau khi đăng ký **không thay đổi** `package_price`, `campaign_positions.bonus_price` hay `total_price` của campaign cũ.

## Dữ liệu

| Bảng | Nội dung |
| --- | --- |
| `ad_position_prices` | Bảng giá hiện hành: `position`, `bonus_price`, `active`. Một position chỉ có một cấu hình. |
| `ad_campaigns` | Snapshot phần giá gói trong `package_price` và tổng thanh toán trong `total_price`. |
| `campaign_positions` | Snapshot từng position đã mua: `campaign_id`, `position`, `bonus_price`. Mỗi campaign-position là duy nhất. |

Migration `V2__add_position_pricing_and_campaign_snapshots.sql` tạo toàn bộ cấu trúc trên. Migration `V3__seed_default_ad_packages_and_position_prices.sql` thêm cấu hình mặc định cho môi trường mới, theo nguyên tắc không ghi đè dữ liệu Admin đã cấu hình.

## Cấu hình mặc định khi triển khai mới

| Loại | Tên/position | Giá (VND) | Trạng thái |
| --- | --- | ---: | --- |
| Gói | Starter (7 ngày) | 100.000 | Active |
| Gói | Standard (14 ngày) | 250.000 | Active |
| Gói | Premium (30 ngày) | 500.000 | Active |
| Position | HOME_TOP | 50.000 | Active |
| Position | HOME_MIDDLE | 30.000 | Active |
| Position | EVENT_DETAIL | 20.000 | Active |

## Luồng nghiệp vụ

1. Admin tạo/cập nhật bảng giá position. Position inactive vẫn hiển thị trong API quản trị, nhưng không xuất hiện ở API công khai và không thể mua mới.
2. Organizer gọi `POST /api/v1/organizer/ad-campaigns` với gói, phương thức thanh toán và `positions` không rỗng, không trùng nhau.
3. Service kiểm tra gói active và từng position đã được cấu hình, active. Service tính tổng, lưu `ad_campaigns` cùng tất cả dòng snapshot `campaign_positions`, rồi trả breakdown giá.
4. Admin duyệt campaign.
5. Khi Admin tạo/cập nhật banner, `position` banner phải tồn tại trong `campaign_positions` của campaign. Do đó banner không thể được đặt ở vị trí chưa mua. Duyệt ảnh banner không đổi position, nên snapshot đã được kiểm tra từ lúc tạo/cập nhật vẫn có hiệu lực.
6. Public banner API chỉ trả banner active của campaign được duyệt, trong thời gian hiển thị; kết quả được nhóm theo `position`. Trong mỗi nhóm thứ tự là `priority` giảm dần, sau đó là thời điểm tạo mới hơn.
7. PREPAID ghi `fee_amount = total_price`; POSTPAID_ESCROW ghi escrow bằng `total_price` và khi publish settlement cũng dùng `total_price`.

## API

### Admin: cấu hình phụ phí position

| Method | URL | Mục đích |
| --- | --- | --- |
| GET | `/api/v1/admin/ad-position-prices` | Danh sách cả active và inactive |
| POST | `/api/v1/admin/ad-position-prices` | Tạo bảng giá position |
| PUT | `/api/v1/admin/ad-position-prices/{id}` | Sửa giá/trạng thái |
| DELETE | `/api/v1/admin/ad-position-prices/{id}` | Xóa cấu hình hiện hành |
| GET | `/api/v1/public/ad-position-prices` | Danh sách position active để Organizer chọn |

Body tạo/sửa:

```json
{
  "position": "HOME_TOP",
  "bonusPrice": 50000,
  "active": true
}
```

### Organizer: đăng ký campaign

`POST /api/v1/organizer/ad-campaigns`

```json
{
  "packageId": "11111111-1111-1111-1111-111111111111",
  "name": "Tết 2026",
  "pricingMode": "PREPAID",
  "positions": ["HOME_TOP", "HOME_MIDDLE"],
  "startsAt": "2026-12-01T00:00:00",
  "endsAt": "2026-12-10T00:00:00"
}
```

Response campaign trả `packagePrice`, `positions` (mỗi position kèm `bonusPrice`) và `totalPrice` để frontend hiển thị breakdown trước/sau khi tạo.

### Public: banner

`GET /api/v1/public/banners` trả `bannersByPosition`, ví dụ:

```json
{
  "bannersByPosition": {
    "HOME_TOP": [{ "priority": 100, "imageUrl": "..." }],
    "EVENT_DETAIL": [{ "priority": 10, "imageUrl": "..." }]
  }
}
```

Các enum position hiện có: `HOME_TOP`, `HOME_MIDDLE`, `EVENT_DETAIL`.

## Validation và lỗi nghiệp vụ

- `positions` rỗng: validation 400.
- Position trùng trong request: 400, `Danh sách position không được trùng lặp`.
- Position chưa có giá hoặc inactive: 400; campaign mới không được tạo.
- `bonusPrice` và giá gói không âm; thời gian kết thúc phải sau thời gian bắt đầu.
- Position banner không thuộc campaign snapshot: 400, `Position banner không thuộc các position đã đăng ký của campaign`.
