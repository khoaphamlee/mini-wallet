# ADR 0004: Dùng outbox pattern để phát sự kiện

- Trạng thái: Đã chấp nhận
- Ngày: 09/11/2026

## Bối cảnh

Sau khi chuyển tiền hoặc nạp tiền thành công, hệ thống cần phát sự kiện (`TransferSucceeded`, `TopupSucceeded`) để gửi email thông báo, và sau này cho các tính năng khác.

Ghi database và gửi message lên RabbitMQ là hai hệ thống khác nhau, không chung transaction:

- Commit xong mới gửi: app sập giữa hai bước thì mất sự kiện.
- Gửi trước rồi mới commit: commit thất bại thì đã thông báo cho một giao dịch không tồn tại.

## Quyết định

1. Ghi sự kiện vào bảng `outbox_events` **trong cùng transaction** với giao dịch.
2. Một job (outbox relay) chạy mỗi 2 giây, lấy tối đa 100 sự kiện `NEW` bằng `SELECT ... FOR UPDATE SKIP LOCKED`, gửi lên RabbitMQ với publisher confirm, rồi đánh dấu `SENT`.
3. Gửi lỗi thì tăng `attempts`. Quá 10 lần thì chuyển `FAILED` để xem tay.
4. Bên nhận xử lý idempotent: lưu `event id` đã xử lý, gặp lại thì bỏ qua. Lý do: relay có thể gửi một sự kiện hai lần nếu sập sau khi gửi mà trước khi kịp đánh dấu `SENT` (at-least-once).

## Các phương án đã cân nhắc

| Phương án | Ưu điểm | Nhược điểm |
| --- | --- | --- |
| **Outbox + job relay** (chọn) | Không mất sự kiện, không phát sự kiện giả. Chỉ cần PostgreSQL và một job | Trễ vài giây. Thêm một bảng và một job |
| `@TransactionalEventListener(AFTER_COMMIT)` gửi thẳng RabbitMQ | Đơn giản, gần như tức thì | App sập sau commit là mất sự kiện |
| Change Data Capture (Debezium đọc WAL) | Không cần job polling, trễ thấp | Thêm Kafka Connect và Debezium, quá nặng cho dự án này |

## Hệ quả

- Email thông báo có thể đến trễ vài giây. Chấp nhận được.
- Cần index một phần `outbox_events (created_at) WHERE status = 'NEW'` để job lấy nhanh.
- Cần job dọn các sự kiện `SENT` cũ hơn 30 ngày (làm sau MVP).
