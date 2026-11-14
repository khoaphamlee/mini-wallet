# ADR 0002: Lưu tiền bằng số nguyên BIGINT (đơn vị đồng)

- Trạng thái: Đã chấp nhận
- Ngày: 09/11/2026

## Bối cảnh

Hệ thống chỉ dùng VND. VND không có đơn vị nhỏ hơn đồng trong giao dịch thực tế. Mọi phép tính tiền phải chính xác tuyệt đối.

Kiểu `double` và `float` biểu diễn số thập phân gần đúng: `0.1 + 0.2` cho ra `0.30000000000000004`. Cộng dồn hàng triệu giao dịch sẽ lệch tiền.

## Quyết định

- Database: cột tiền kiểu `BIGINT`, đơn vị đồng.
- Java: kiểu `long` trong entity. Ở tầng domain bọc trong một value object `Money` (record) để có các phép `plus`, `minus`, `isNegative` và chặn tràn số bằng `Math.addExact`, `Math.subtractExact`.
- API: số nguyên trong JSON, ví dụ `"amount": 150000`.

## Các phương án đã cân nhắc

| Phương án | Ưu điểm | Nhược điểm |
| --- | --- | --- |
| **BIGINT / long** (chọn) | Chính xác, nhanh, đơn giản, đủ chứa 9,2 × 10^18 đồng | Nếu thêm ngoại tệ có số lẻ phải đổi sang đơn vị nhỏ nhất (cent) |
| NUMERIC(19,2) / BigDecimal | Chính xác, hỗ trợ số lẻ | Dễ dùng sai (`new BigDecimal(0.1)`, so sánh bằng `equals` khác `compareTo`), chậm hơn, không cần thiết với VND |
| DOUBLE / double | Tiện | Sai số làm tròn. Không bao giờ dùng cho tiền |

## Hệ quả

- Không có phép chia trong nghiệp vụ hiện tại. Nếu sau này có phí theo phần trăm, quy tắc làm tròn phải ghi rõ trong PRD và tính bằng số nguyên.
- Frontend tự định dạng hiển thị (`150.000 ₫`) bằng `Intl.NumberFormat('vi-VN')`.
