# ADR 0005: Ví hệ thống cho tiền nạp vào, không khóa khi ghi

- Trạng thái: Đã chấp nhận
- Ngày: 09/11/2026

## Bối cảnh

Sổ cái ghi sổ kép yêu cầu mỗi giao dịch có ít nhất một dòng trừ và một dòng cộng, tổng bằng 0. Khi nạp tiền, ví người dùng được cộng, vậy dòng trừ ghi vào đâu?

Nếu dòng trừ ghi vào một ví hệ thống và ví đó bị khóa (`FOR UPDATE`) như ví thường, mọi lệnh nạp tiền trên toàn hệ thống phải xếp hàng qua **một dòng duy nhất** trong database (hot row).

## Quyết định

1. Tạo ví hệ thống `SYSTEM_TOPUP` (`type = 'SYSTEM'`, không thuộc người dùng nào) bằng migration Flyway.
2. Mỗi lệnh nạp thành công ghi hai dòng ledger: `-amount` cho `SYSTEM_TOPUP`, `+amount` cho ví người dùng.
3. Ví hệ thống **không bị khóa** và cột `balance` của nó **không cập nhật** trong lúc nạp tiền. Dòng ledger của nó có `balance_after = NULL`.
4. Job đối soát hằng đêm tính lại `balance` của ví hệ thống bằng tổng ledger. Số dư này luôn âm và bằng tổng tiền đã nạp vào hệ thống.
5. Ràng buộc database cho phép ví hệ thống âm: `CHECK (type = 'SYSTEM' OR balance >= 0)`.

## Các phương án đã cân nhắc

| Phương án | Ưu điểm | Nhược điểm |
| --- | --- | --- |
| **Ví hệ thống, chỉ ghi ledger** (chọn) | Giữ được bất biến tổng ledger bằng 0. Không có hot row | Số dư ví hệ thống chỉ đúng sau khi đối soát |
| Ví hệ thống, khóa và cập nhật như ví thường | Số dư luôn đúng tức thì | Mọi lệnh nạp tuần tự qua một dòng, nghẽn khi tải cao |
| Không ghi dòng đối ứng cho nạp tiền | Đơn giản nhất | Mất bất biến tổng bằng 0, đối soát không còn kiểm tra được toàn hệ thống |

## Hệ quả

- Đối soát có hai kiểm tra: số dư từng ví khách hàng bằng tổng ledger của ví đó, và tổng ledger toàn hệ thống bằng 0.
- Nếu sau này có rút tiền, thêm ví hệ thống `SYSTEM_WITHDRAW` theo cùng cách.
