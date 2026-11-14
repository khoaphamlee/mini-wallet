# ADR 0003: Dùng pessimistic locking cho chuyển tiền

- Trạng thái: Đã chấp nhận
- Ngày: 09/11/2026

## Bối cảnh

Hai request cùng đọc số dư 100.000đ, cùng thấy đủ để chuyển 80.000đ, rồi cùng trừ tiền. Kết quả: số dư âm hoặc hệ thống mất tiền (lost update). Đây là lỗi race condition kinh điển của ví điện tử.

Một ví có thể nhận nhiều request cùng lúc (người dùng bấm nhiều lần, script tự động, nhiều người cùng chuyển vào một ví).

## Quyết định

Trong transaction chuyển tiền:

1. Khóa cả hai ví bằng `SELECT ... FOR UPDATE` (Spring Data JPA: `@Lock(LockModeType.PESSIMISTIC_WRITE)`).
2. **Luôn khóa ví có id nhỏ hơn trước**, bất kể ví nào là ví gửi, để tránh deadlock khi A chuyển B và B chuyển A cùng lúc.
3. Kiểm tra số dư và hạn mức **sau khi đã khóa**.
4. Đặt thời gian chờ khóa khoảng 3 giây (`jakarta.persistence.lock.timeout`). Quá thời gian thì trả 503 `SERVICE_BUSY`.
5. Giữ `CHECK (type = 'SYSTEM' OR balance >= 0)` ở database làm lớp bảo vệ cuối.

## Các phương án đã cân nhắc

| Phương án | Ưu điểm | Nhược điểm |
| --- | --- | --- |
| **Pessimistic locking** (chọn) | Đúng ngay lần đầu, không cần retry. Hành vi dễ đoán khi tranh chấp cao trên một ví | Request phải chờ nhau trên cùng một ví. Phải cẩn thận thứ tự khóa để tránh deadlock |
| Optimistic locking (`@Version` + retry) | Không giữ khóa, tốt khi ít tranh chấp | Khi nhiều request vào cùng một ví, phần lớn thất bại và retry liên tục. Code retry phức tạp hơn |
| Câu UPDATE có điều kiện (`UPDATE wallets SET balance = balance - :x WHERE id = :id AND balance >= :x`) | Rất nhanh, không cần khóa tường minh | Khó kết hợp kiểm tra nghiệp vụ khác (hạn mức ngày, trạng thái ví) và ghi `balance_after` cho sổ cái |
| Isolation level SERIALIZABLE | Database tự phát hiện xung đột | Lỗi serialization phải retry, khó dự đoán hiệu năng, khó giải thích |

## Hệ quả

- Test đồng thời (100 luồng cùng chuyển từ một ví) là bắt buộc trong CI để chứng minh quyết định này đúng. Chạy thêm một lần khi bỏ khóa để thấy test thất bại, lưu kết quả vào README.
- Transaction chuyển tiền phải ngắn: mọi kiểm tra không cần khóa (PIN, số tiền, người nhận) làm trước khi khóa.
- `wallets.version` vẫn giữ cho các thao tác hiếm và ít tranh chấp như admin đổi trạng thái ví.
