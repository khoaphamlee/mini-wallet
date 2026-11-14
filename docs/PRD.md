# PRD: Mini Wallet

| Mục | Nội dung |
| --- | --- |
| Phiên bản | 0.1 (bản nháp) |
| Ngày | 09/11/2026 |
| Người viết | Khoa Phạm |
| Trạng thái | Đang hoàn thiện |

## 1. Bài toán

Một nhóm nhỏ (bạn bè, câu lạc bộ, nhóm đi du lịch) cần chuyển tiền qua lại thường xuyên với số tiền nhỏ. Chuyển khoản ngân hàng thì phải nhớ số tài khoản, và khó xem lại ai đã chuyển cho ai.

Mini Wallet là ví điện tử nội bộ: mỗi người có một ví, nạp tiền vào ví, chuyển cho nhau bằng số điện thoại, xem lịch sử rõ ràng.

Mục tiêu kỹ thuật của dự án quan trọng ngang mục tiêu sản phẩm: **số dư luôn đúng** dù có lỗi mạng, request gửi trùng hay nhiều request đến cùng lúc.

## 2. Vai trò người dùng

| Vai trò | Mô tả |
| --- | --- |
| Khách hàng (`CUSTOMER`) | Người dùng cuối: nạp tiền, chuyển tiền, xem lịch sử |
| Quản trị (`ADMIN`) | Vận hành hệ thống: khóa/mở ví, xem giao dịch |
| Hệ thống | Các job tự động: đối soát, hủy giao dịch quá hạn, gửi thông báo |

## 3. Phạm vi

### 3.1. MVP (mốc v0.1.0, tuần 8)

| Mã | Tính năng |
| --- | --- |
| F1 | Đăng ký, đăng nhập, đăng xuất, làm mới token |
| F2 | Thiết lập và đổi mã PIN giao dịch |
| F3 | Tự tạo ví khi đăng ký, xem số dư |
| F4 | Nạp tiền qua cổng thanh toán giả lập |
| F5 | Tra cứu người nhận theo số điện thoại |
| F6 | Chuyển tiền giữa hai ví, có PIN và hạn mức |
| F7 | Lịch sử giao dịch: lọc theo loại, trạng thái, khoảng ngày; phân trang |

### 3.2. Sau MVP (tuần 9 đến 12)

| Mã | Tính năng |
| --- | --- |
| F8 | Rate limiting, audit log |
| F9 | Email thông báo khi nhận tiền (qua RabbitMQ, outbox pattern) |
| F10 | Job đối soát số dư hằng đêm |
| F11 | Job hủy giao dịch nạp tiền treo quá 15 phút |
| F12 | Admin: xem ví, khóa và mở ví |

### 3.3. Ngoài phạm vi

Những thứ cố ý **không** làm, để dự án không phình ra:

- Liên kết tài khoản ngân hàng thật, rút tiền về ngân hàng
- Nhiều loại tiền tệ (chỉ dùng VND)
- eKYC, xác minh danh tính
- Ứng dụng di động
- Microservices (xem ADR 0001)
- Hoàn tiền tự động. Nếu cần hoàn tiền, admin tạo giao dịch đảo chiều (làm sau)

## 4. User story

Định dạng: *Là [vai trò], tôi muốn [hành động], để [lợi ích].* Tiêu chí nghiệm thu viết theo Given / When / Then.

### US-01. Đăng ký (F1, F3)

Là khách hàng, tôi muốn đăng ký bằng email, số điện thoại và mật khẩu, để có ví riêng.

- Given email và số điện thoại chưa tồn tại, When đăng ký với dữ liệu hợp lệ, Then tạo tài khoản và một ví có số dư 0đ.
- Given email hoặc số điện thoại đã tồn tại, When đăng ký, Then trả lỗi `EMAIL_ALREADY_EXISTS` hoặc `PHONE_ALREADY_EXISTS`, không tạo gì thêm.
- Given mật khẩu dưới 8 ký tự hoặc thiếu chữ số, When đăng ký, Then trả lỗi `VALIDATION_ERROR` kèm tên trường sai.

### US-02. Đăng nhập (F1)

Là khách hàng, tôi muốn đăng nhập, để dùng ví của mình.

- Given đúng email và mật khẩu, When đăng nhập, Then nhận access token (hạn 15 phút) và refresh token (hạn 7 ngày).
- Given sai mật khẩu, When đăng nhập, Then trả lỗi `INVALID_CREDENTIALS`, không tiết lộ email có tồn tại hay không.

### US-03. Làm mới token và đăng xuất (F1)

Là khách hàng, tôi muốn phiên đăng nhập được giữ mà không phải nhập lại mật khẩu, và đăng xuất được khi cần.

- Given refresh token còn hạn, When gọi làm mới, Then nhận cặp token mới và refresh token cũ bị vô hiệu.
- Given đã đăng xuất, When dùng lại refresh token cũ, Then trả lỗi `INVALID_TOKEN`.

### US-04. Thiết lập mã PIN (F2)

Là khách hàng, tôi muốn đặt mã PIN 6 chữ số, để mỗi lần chuyển tiền có thêm một lớp xác nhận.

- Given chưa có PIN, When đặt PIN 6 chữ số, Then lưu PIN dạng băm.
- Given đã có PIN, When đổi PIN, Then phải nhập đúng PIN hiện tại.
- Given chưa có PIN, When chuyển tiền, Then trả lỗi `PIN_NOT_SET`.

### US-05. Xem số dư (F3)

Là khách hàng, tôi muốn xem số dư hiện tại, để biết mình còn bao nhiêu tiền.

- Given đã đăng nhập, When mở trang chủ, Then thấy số dư và trạng thái ví.

### US-06. Nạp tiền (F4)

Là khách hàng, tôi muốn nạp tiền vào ví qua cổng thanh toán, để có tiền chuyển cho người khác.

- Given số tiền từ 10.000đ đến 50.000.000đ, When tạo lệnh nạp, Then tạo giao dịch `PENDING` và trả về link thanh toán.
- Given cổng thanh toán báo thành công với chữ ký hợp lệ, When nhận callback, Then giao dịch chuyển sang `SUCCESS` và số dư tăng đúng số tiền.
- Given callback cho cùng một lệnh đến lần thứ hai, When xử lý, Then bỏ qua, số dư không tăng thêm.
- Given chữ ký callback sai, When nhận callback, Then từ chối và ghi log cảnh báo.

### US-07. Tra cứu người nhận (F5)

Là khách hàng, tôi muốn nhập số điện thoại và thấy tên người nhận, để chắc mình không chuyển nhầm.

- Given số điện thoại có ví đang hoạt động, When tra cứu, Then thấy họ tên đã che bớt (ví dụ `NGUYEN V*** A`).
- Given số điện thoại không tồn tại, When tra cứu, Then trả lỗi `RECIPIENT_NOT_FOUND`.

### US-08. Chuyển tiền (F6)

Là khách hàng, tôi muốn chuyển tiền cho người khác bằng số điện thoại, để không phải nhớ số tài khoản.

- Given số dư 100.000đ và PIN đúng, When chuyển 30.000đ, Then số dư còn 70.000đ, người nhận tăng 30.000đ, có một giao dịch `SUCCESS`.
- Given số dư 20.000đ, When chuyển 30.000đ, Then trả lỗi `INSUFFICIENT_BALANCE`, không ví nào thay đổi.
- Given sai PIN, When chuyển, Then trả lỗi `INVALID_PIN`. Sai 5 lần liên tiếp thì khóa chuyển tiền 15 phút (`PIN_LOCKED`).
- Given người nhận là chính mình, When chuyển, Then trả lỗi `SELF_TRANSFER_NOT_ALLOWED`.
- Given ví gửi hoặc ví nhận bị khóa, When chuyển, Then trả lỗi `WALLET_LOCKED`.
- Given tổng đã chuyển trong ngày cộng số tiền mới vượt 50.000.000đ, When chuyển, Then trả lỗi `DAILY_LIMIT_EXCEEDED`.

### US-09. Không bị trừ tiền hai lần (F6)

Là khách hàng, tôi muốn bấm nhầm hai lần hay mạng chập chờn cũng không bị trừ tiền hai lần.

- Given cùng một `Idempotency-Key`, When gửi request lần hai sau khi lần một đã xong, Then nhận lại đúng kết quả lần một, không tạo giao dịch mới.
- Given cùng một `Idempotency-Key`, When gửi lần hai trong lúc lần một chưa xong, Then trả lỗi `DUPLICATE_REQUEST` (409).
- Given cùng một `Idempotency-Key` nhưng nội dung request khác, When gửi, Then trả lỗi `IDEMPOTENCY_KEY_REUSED` (422).

### US-10. Xem lịch sử giao dịch (F7)

Là khách hàng, tôi muốn xem lại các giao dịch, để biết tiền đi đâu và đến từ đâu.

- Given đã đăng nhập, When mở lịch sử, Then thấy giao dịch mới nhất trước, mỗi trang 20 dòng.
- Given chọn lọc theo loại, trạng thái hoặc khoảng ngày, When áp dụng, Then chỉ thấy giao dịch khớp điều kiện.
- Given mở giao dịch của người khác qua id, When gọi API, Then trả lỗi `TRANSACTION_NOT_FOUND` (không tiết lộ giao dịch có tồn tại).

### US-11. Admin khóa ví (F12)

Là admin, tôi muốn khóa ví có dấu hiệu bất thường, để ngăn thiệt hại.

- Given ví đang hoạt động, When khóa kèm lý do, Then ví chuyển sang `LOCKED` và có audit log ghi người khóa, lý do, thời điểm.
- Given ví đã khóa, When chủ ví chuyển hoặc nhận tiền, Then trả lỗi `WALLET_LOCKED`.

### US-12. Đối soát hằng đêm (F10)

Là admin, tôi muốn hệ thống tự kiểm tra số dư mỗi đêm, để phát hiện sai lệch sớm.

- Given mọi ví khách hàng, When job chạy lúc 02:00, Then số dư mỗi ví bằng tổng `amount` của sổ cái ví đó, và tổng `amount` toàn hệ thống bằng 0.
- Given có ví lệch, When job chạy, Then ghi log `ERROR` kèm id ví và gửi cảnh báo.

## 5. Yêu cầu phi chức năng

| Mã | Nhóm | Yêu cầu | Cách kiểm chứng |
| --- | --- | --- | --- |
| NFR-1 | Đúng đắn | 100 request chuyển tiền đồng thời từ một ví cho số dư cuối đúng, không bao giờ âm | Test đồng thời trong CI |
| NFR-2 | Đúng đắn | Không trừ tiền hai lần cho cùng một `Idempotency-Key` | Integration test |
| NFR-3 | Hiệu năng | API chuyển tiền p95 dưới 300 ms ở 200 request/giây | Load test bằng k6 |
| NFR-4 | Hiệu năng | API lịch sử giao dịch p95 dưới 200 ms với ví có 100.000 giao dịch | Load test bằng k6 |
| NFR-5 | Bảo mật | Mật khẩu và PIN băm bằng BCrypt, không ghi mật khẩu, PIN, token vào log | Review code, test log |
| NFR-6 | Bảo mật | Người dùng chỉ xem và thao tác trên ví, giao dịch của chính mình | Test truy cập chéo |
| NFR-7 | Truy vết | Mọi thay đổi số dư truy ngược được tới một giao dịch và các dòng sổ cái | Thiết kế database, job đối soát |
| NFR-8 | Vận hành | Có health check, metrics, log JSON kèm `traceId` | Kiểm tra trên môi trường prod |
| NFR-9 | Chất lượng | Độ phủ test tối thiểu 70% cho tầng service và domain | JaCoCo trong CI |

Các con số hiệu năng là mục tiêu tự đặt, sẽ đo lại ở tuần 12 và cập nhật vào `docs/performance.md`.

## 6. Quy tắc nghiệp vụ

| Mã | Quy tắc | Giá trị |
| --- | --- | --- |
| BR-01 | Đơn vị tiền | VND, lưu bằng số nguyên `BIGINT` (đơn vị đồng). Không dùng `double`, `float` |
| BR-02 | Số dư ví khách hàng | Không bao giờ âm |
| BR-03 | Nạp tiền mỗi lần | Tối thiểu 10.000đ, tối đa 50.000.000đ |
| BR-04 | Chuyển tiền mỗi lần | Tối thiểu 1.000đ, tối đa 20.000.000đ |
| BR-05 | Hạn mức chuyển mỗi ngày | 50.000.000đ, tính theo ngày giờ Việt Nam (UTC+7), chỉ cộng giao dịch `SUCCESS` |
| BR-06 | Mã PIN | 6 chữ số. Sai 5 lần liên tiếp khóa chuyển tiền 15 phút. Đúng một lần thì đếm lại từ 0 |
| BR-07 | Giao dịch đã thành công | Không được sửa, không được xóa |
| BR-08 | Ví bị khóa | Không chuyển đi, không nhận được, vẫn xem được số dư và lịch sử |
| BR-09 | Lệnh nạp tiền treo | `PENDING` quá 15 phút thì chuyển sang `FAILED` |
| BR-10 | Idempotency-Key | Bắt buộc với API tạo giao dịch. UUID, có hiệu lực 24 giờ, gắn với từng người dùng |
| BR-11 | Ghi chú chuyển tiền | Tối đa 100 ký tự |
| BR-12 | Mật khẩu | Tối thiểu 8 ký tự, có ít nhất một chữ và một chữ số |
| BR-13 | Token | Access token 15 phút, refresh token 7 ngày, refresh token dùng một lần |
| BR-14 | Rate limit | Đăng nhập 5 lần/phút mỗi IP. Chuyển tiền 10 lần/phút mỗi người dùng |

## 7. Thuật ngữ

| Thuật ngữ | Nghĩa |
| --- | --- |
| Ví (wallet) | Tài khoản chứa tiền của một người dùng |
| Ví hệ thống | Ví nội bộ đại diện cho tiền đi vào hệ thống từ cổng thanh toán (xem ADR 0005) |
| Giao dịch (transaction) | Một thao tác nghiệp vụ: nạp tiền hoặc chuyển tiền |
| Sổ cái (ledger) | Danh sách các dòng cộng/trừ tiền. Mỗi giao dịch thành công sinh ít nhất hai dòng |
| Idempotency-Key | Mã do client tạo để server nhận ra request bị gửi lại |
| Đối soát | Kiểm tra số dư lưu trong ví có khớp với sổ cái không |

## 8. Câu hỏi còn mở

- Có cho phép người dùng hủy tài khoản khi ví còn tiền không? Tạm thời: không.
- Có cần xác thực email khi đăng ký không? Tạm thời: không, đưa vào sau MVP nếu còn thời gian.
- Email thông báo gửi qua dịch vụ nào? Tạm thời dùng Mailpit trên máy local, chọn dịch vụ thật khi deploy.
