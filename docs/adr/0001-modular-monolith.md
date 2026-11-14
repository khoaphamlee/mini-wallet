# ADR 0001: Chọn modular monolith thay vì microservices

- Trạng thái: Đã chấp nhận
- Ngày: 09/11/2026

## Bối cảnh

Hệ thống có 6 mảng nghiệp vụ (auth, wallet, transfer, payment, ledger, notification). Dự án do một người làm, thời gian khoảng 12 tuần, chạy trên một VPS nhỏ.

Chuyển tiền cần cập nhật số dư hai ví, ghi giao dịch, ghi sổ cái và ghi sự kiện trong **cùng một transaction** để không bao giờ lệch tiền.

## Quyết định

Xây một ứng dụng Spring Boot duy nhất, chia module theo nghiệp vụ, dùng chung một PostgreSQL. Ranh giới giữa các module được giữ bằng quy ước package và kiểm tra tự động bằng ArchUnit hoặc Spring Modulith trong CI.

## Các phương án đã cân nhắc

| Phương án | Ưu điểm | Nhược điểm |
| --- | --- | --- |
| **Modular monolith** (chọn) | Một transaction database cho chuyển tiền. Deploy, debug, test đơn giản. Chạy được trên VPS 2 đến 4 GB | Cả hệ thống scale chung. Ranh giới module phải tự giữ kỷ luật |
| Microservices (wallet, transfer, payment tách riêng) | Scale và deploy từng phần độc lập | Chuyển tiền thành giao dịch phân tán, phải dùng Saga và bù trừ. Cần service discovery, gateway, tracing phân tán. Quá nặng cho một người trong 12 tuần |
| Monolith không chia module | Nhanh lúc đầu | Code dính chặt vào nhau, khó tách sau này, khó giải thích thiết kế khi phỏng vấn |

## Hệ quả

- Chuyển tiền dùng transaction cục bộ của PostgreSQL, không cần Saga.
- Nếu sau này cần tách, `notification` là module tách đầu tiên vì nó đã giao tiếp qua RabbitMQ. `payment` là module thứ hai.
- Phải viết test kiến trúc để bắt vi phạm ranh giới module ngay trong CI.
