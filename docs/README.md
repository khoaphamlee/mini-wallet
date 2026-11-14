# Tài liệu Mini Wallet

| File | Nội dung | Đọc khi nào |
| --- | --- | --- |
| [PRD.md](PRD.md) | Bài toán, phạm vi, user story, quy tắc nghiệp vụ | Trước khi làm bất kỳ tính năng nào |
| [architecture.md](architecture.md) | Module, luồng chuyển tiền và nạp tiền, idempotency, bảo mật, lỗi, triển khai | Khi thiết kế hoặc review code |
| [database.md](database.md) | Bảng, ràng buộc, index, DBML cho dbdiagram.io | Khi viết migration Flyway |
| [openapi.yaml](openapi.yaml) | Đặc tả REST API | Khi viết controller và frontend. Mở bằng [Swagger Editor](https://editor.swagger.io) |
| [adr/](adr/) | Các quyết định kiến trúc và lý do | Khi ai đó hỏi "tại sao làm vậy?" |

## Danh sách ADR

| Số | Quyết định |
| --- | --- |
| [0001](adr/0001-modular-monolith.md) | Modular monolith thay vì microservices |
| [0002](adr/0002-money-as-bigint.md) | Lưu tiền bằng BIGINT, đơn vị đồng |
| [0003](adr/0003-pessimistic-locking-for-transfers.md) | Pessimistic locking cho chuyển tiền |
| [0004](adr/0004-outbox-pattern.md) | Outbox pattern để phát sự kiện |
| [0005](adr/0005-system-wallet.md) | Ví hệ thống cho tiền nạp vào, không khóa khi ghi |

ADR mới: chép [adr/template.md](adr/template.md), đánh số tiếp theo. ADR đã chấp nhận thì không sửa nội dung; muốn đổi quyết định thì viết ADR mới và đánh dấu ADR cũ là "Bị thay thế bởi ADR xxxx".
