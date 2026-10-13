# Hướng dẫn demo (tiếng Việt)

Kịch bản demo hệ thống **Omnichannel E-Commerce** chạy thật trên AWS: `https://api.cunghuynh.id.vn`.
Thời lượng gợi ý: **15-20 phút**. Mỗi phần có: *nói gì*, *làm gì*, *kết quả mong đợi*.

> Mật khẩu admin và các thông tin đăng nhập nằm trong file `credentials.local.md` trên máy bạn (đã được git bỏ qua, **không** nằm trên GitHub).
> Cấu hình, triển khai, trỏ tên miền: xem [DEPLOY.md](DEPLOY.md). Chi tiết kỹ thuật: [report.md](report.md).

---

## 0. Chuẩn bị trước giờ demo (làm trước 15 phút)

### 0.1. Kiểm tra server

```bash
ssh -i main-key.pem ubuntu@100.54.240.124
cd ~/omnichannel/deploy
docker compose -f docker-compose.production.yml ps        # tất cả phải "healthy" / "running"
```

Nếu server đang tắt: vào AWS Console -> EC2 -> Start instance. **Lưu ý:** nếu không gắn Elastic IP thì IP đổi, phải sửa lại bản ghi DNS (`api`, `grafana`) và secret `VPS_HOST` trên GitHub.

### 0.2. Bật những thứ chỉ dùng khi demo (trên server, trong `~/omnichannel/deploy/.env`)

| Biến | Giá trị khi demo | Để làm gì |
|---|---|---|
| `SWAGGER_ENABLED` | `true` | giao diện Swagger để bấm thử API |
| `COMPOSE_PROFILES` | `monitoring` | Prometheus + Grafana + Loki |
| `MAIL_USER`, `MAIL_PASSWORD` | thông tin SMTP của [Mailtrap](https://mailtrap.io) | để thấy email thật sự được gửi |
| `PAYMENT_MOCK_ENABLED` | `true` | thanh toán giả lập (không cần VNPay/MoMo) |

Sau khi sửa `.env`:

```bash
docker compose -f docker-compose.production.yml up -d
```

### 0.3. Mở sẵn các tab trình duyệt

| Tab | Địa chỉ |
|---|---|
| Swagger UI | https://api.cunghuynh.id.vn/swagger-ui.html |
| Grafana | https://grafana.cunghuynh.id.vn (user `admin`, mật khẩu trong `credentials.local.md`) |
| Hộp thư email | Mailtrap -> Email Testing -> Inboxes |
| Terminal SSH | đã `cd ~/omnichannel/deploy` |
| (tuỳ chọn) Postman | đã import `docs/postman/` |

### 0.4. Đăng nhập Swagger một lần

1. Trong Swagger chọn `identity` -> `POST /api/auth/login` -> **Try it out**
2. Body: `{"email":"cung.h28@gmail.com","password":"<mật khẩu admin>"}` -> **Execute**
3. Copy `data.accessToken` -> bấm **Authorize** (góc phải) -> dán token (không gõ chữ `Bearer`) -> **Authorize**.
   Token này dùng được cho cả 4 dịch vụ và được nhớ khi tải lại trang.
4. Đăng nhập admin chỉ để làm việc quản trị. Với các bước của khách hàng, tạo thêm một tài khoản khách (`POST /api/auth/register`) và đổi token trong **Authorize** khi cần.

### 0.5. Kiểm tra dữ liệu mẫu

`GET /api/products` phải trả về 3 sản phẩm: `FLASH-001` (199.000đ, kho 10), `PHONE-001` (5.990.000đ, kho 500), `EARBUD-001` (790.000đ, kho 500). Nếu rỗng, chạy trên máy bạn:

```bash
BASE=https://api.cunghuynh.id.vn ADMIN_EMAIL=cung.h28@gmail.com ADMIN_PASSWORD='<mật khẩu admin>' bash scripts/seed.sh
```

---

## 1. Giới thiệu kiến trúc (2 phút)

**Nói:** hệ thống tách thành 6 microservice độc lập chạy trong Docker trên một máy chủ AWS EC2, mỗi service có database riêng (Database per Service), giao tiếp đồng bộ bằng REST/gRPC và bất đồng bộ bằng RabbitMQ.

```
Khách hàng --HTTPS--> Nginx (443, chứng chỉ Let's Encrypt) --> API Gateway :8080
                                                          |  xác thực JWT, giới hạn tốc độ, phân quyền
        +---------------+---------------+-----------------+----------------+
   identity-service  product-service  order-inventory   payment-service   notification-service
   (PostgreSQL)      (MongoDB)        (PostgreSQL+Redis) (PostgreSQL)      (MongoDB, email)
                          ^ gRPC             |  RabbitMQ: order.created / payment.* / order.status-changed
                          +------------------+
```

Điểm nhấn để nói:
- **Gateway** là cửa duy nhất: kiểm tra JWT, xoá mọi header `X-User-*` do client gửi rồi tự gắn lại từ token.
- **Order <-> Product** dùng **gRPC** để lấy tên và giá tại thời điểm mua (lưu snapshot vào đơn hàng).
- **Saga (choreography) + Outbox:** đặt hàng -> giữ kho -> thanh toán -> xác nhận, hoặc **huỷ và hoàn kho** nếu thanh toán thất bại.
- Chỉ mở cổng 22, 80, 443 ra Internet. Database, Redis, RabbitMQ chỉ nằm trong mạng nội bộ Docker.

---

## 2. Demo 1: luồng đặt hàng từ đầu đến cuối (4 phút)

**Nói:** đây là Saga: các service phối hợp bằng sự kiện qua RabbitMQ, không có giao dịch phân tán.

1. **Đăng ký khách hàng:** `identity` -> `POST /api/auth/register`
   ```json
   {"email":"khach1@example.com","password":"Passw0rd!","fullName":"Nguyen Van A"}
   ```
   -> trả **201**. Lấy `accessToken` mới và **Authorize** lại (đổi sang token khách).
2. **Xem sản phẩm (không cần đăng nhập):** `product` -> `GET /api/products?q=omni`
3. **Đặt hàng:** `order` -> `POST /api/orders`, thêm header `Idempotency-Key` = `demo-001`
   ```json
   {
     "items": [{"sku": "PHONE-001", "quantity": 1}],
     "shipping": {"receiverName": "Nguyen Van A", "phone": "0900000000", "address": "1 Le Loi, Quan 1, TP.HCM"},
     "paymentMethod": "MOCK"
   }
   ```
   -> **201**, `status: PENDING`, `totalAmount: 5990000`. Ghi lại `id` của đơn hàng.
4. **Xem link thanh toán** (được tạo bất đồng bộ sau ~1 giây): `payment` -> `GET /api/payments/order/{orderId}` -> `status: PENDING`, có `paymentUrl`.
5. **Thanh toán giả lập:** `payment` -> `POST /api/payments/mock/{orderId}/complete`, `success=true` -> `result: APPLIED`.
6. **Đợi 2-3 giây**, rồi `order` -> `GET /api/orders/{id}` -> **`status: CONFIRMED`**.
7. **Email:** mở hộp thư Mailtrap: có "We received your order" và "Order ... is confirmed".
8. **Kho hàng (cần quyền admin):** đổi lại token admin -> `GET /api/inventory/PHONE-001` -> `available` giảm 1, `reserved: 0`.

**Nói thêm:** thanh toán thật (VNPay/MoMo) chỉ khác ở bước 5: khách trả tiền trên trang của cổng thanh toán, cổng gọi webhook (IPN) có **chữ ký HMAC** về hệ thống. Mã đã viết và có unit test, nhưng chưa chạy thử với sandbox thật vì chưa có tài khoản.

---

## 3. Demo 2: chống đặt trùng, Idempotency (1 phút)

Gửi lại **đúng** request đặt hàng ở bước 3 với cùng `Idempotency-Key: demo-001`.
**Kết quả:** trả về **cùng một đơn hàng** (cùng `id`), không tạo đơn mới, không trừ kho thêm.
**Nói:** khi mạng chập chờn client gửi lại request, hệ thống vẫn an toàn.

---

## 4. Demo 3: Saga bù trừ, thanh toán thất bại (2 phút)

1. Đặt đơn mới với `Idempotency-Key: demo-002` (cùng body như trên).
2. Chờ 1-2 giây rồi thanh toán **thất bại:** `POST /api/payments/mock/{orderId}/complete` với `success=false`.
3. Đợi 2-3 giây, `GET /api/orders/{id}` -> **`CANCELLED`**, `cancelReason: Payment failed: ...`.
4. Admin: `GET /api/inventory/PHONE-001` -> số lượng **trở lại như trước** (kho được hoàn).

**Nói:** đây là *compensating transaction*. Ngoài ra đơn `PENDING` quá **15 phút** không thanh toán sẽ tự bị huỷ và hoàn kho (job định kỳ).

---

## 5. Demo 4: tải lớn, tồn kho không sai lệch (4 phút)

**Nói:** kho `FLASH-001` chỉ còn **10**. Bắn hàng nghìn request đặt hàng cùng lúc, kết quả phải là **đúng 10 đơn thành công**, kho không bao giờ âm.

**Cơ chế để nói:** hai lớp bảo vệ: (1) Redis Lua script trừ kho nguyên tử, từ chối sớm phần lớn request thừa; (2) PostgreSQL `UPDATE ... WHERE available >= số_lượng` là nguồn sự thật cuối cùng. Cộng thêm khoá thứ tự SKU để tránh deadlock.

### Chuẩn bị

Gateway mặc định chỉ cho **120 request/phút/IP**. Khi chạy tải phải nâng tạm lên (trên server):

```bash
cd ~/omnichannel/deploy
sed -i 's/^RATE_LIMIT_RPM=.*/RATE_LIMIT_RPM=1000000/' .env
docker compose -f docker-compose.production.yml up -d api-gateway
```

### Chạy (từ máy bạn, cần cài [k6](https://k6.io): `winget install k6`)

```bash
k6 run -e BASE=https://api.cunghuynh.id.vn -e ADMIN_EMAIL=cung.h28@gmail.com -e ADMIN_PASSWORD='<mật khẩu admin>' \
       -e RATE=200 -e DURATION=20s -e STOCK=10 scripts/loadtest/flash-sale.js
```

Hoặc bằng Docker (Git Bash trên Windows cần thêm `MSYS_NO_PATHCONV=1` ở đầu lệnh):

```bash
docker run --rm -i -e BASE=https://api.cunghuynh.id.vn -e ADMIN_EMAIL=cung.h28@gmail.com -e ADMIN_PASSWORD='<mật khẩu admin>' \
  -e RATE=200 -e DURATION=20s -v "$PWD/scripts/loadtest:/scripts" grafana/k6 run /scripts/flash-sale.js
```

### Kết quả mong đợi

```
orders_created.....: 10      <- ĐÚNG bằng số lượng kho
orders_unexpected..: 0
final stock for FLASH-001: available=0 reserved=10
✓ stock never negative
✓ all stock reserved or sold
```

Trong lúc chạy, mở **Grafana** để chụp biểu đồ request rate và JVM.

### Số liệu tôi đã đo (máy local, Docker, 8 CPU, k6 cùng máy)

| Tốc độ mục tiêu | Đơn thành công | Bị từ chối "hết hàng" | Lỗi bất thường | Kho |
|---|---|---|---|---|
| 300 req/s | 10 | hàng nghìn | 0 | đúng |
| 800 req/s (thực tế đạt ~380 req/s) | 10 | 10.073 | 0 | đúng |

**Trung thực khi trình bày:** điều được chứng minh là **tính đúng đắn** (đúng 10 đơn, không âm kho). Thông lượng tối đa phụ thuộc phần cứng: máy EC2 `m7i-flex.large` chỉ có 2 vCPU, nên hãy dùng `RATE` vừa phải (200-300) và **chưa có số đo trên server này**. Hãy chạy thử trước buổi demo.

### Dọn dẹp sau khi chạy

```bash
sed -i 's/^RATE_LIMIT_RPM=.*/RATE_LIMIT_RPM=120/' .env
docker compose -f docker-compose.production.yml up -d api-gateway
```

Các đơn do k6 tạo sẽ ở trạng thái `PENDING` và **tự huỷ sau 15 phút**. Muốn chạy lại ngay thì không cần làm gì thêm (script tự đặt lại kho về 10).

---

## 6. Demo 5: tính sẵn sàng, tắt Notification vẫn đặt hàng được (3 phút)

**Nói:** Notification chỉ là dịch vụ phụ; sập cũng không được ảnh hưởng việc đặt hàng. RabbitMQ giữ lại tin nhắn trong hàng đợi bền vững.

Trên server (terminal SSH):

```bash
# 1. tắt dịch vụ email
docker stop deploy-notification-service-1

# 2. đặt hàng trong Swagger (Idempotency-Key mới) -> VẪN THÀNH CÔNG, status PENDING

# 3. xem tin nhắn đang chờ trong hàng đợi
docker exec deploy-rabbitmq-1 rabbitmqctl list_queues name messages consumers
#    notification.events    N    0     (N tin chờ, 0 consumer)

# 4. bật lại
docker start deploy-notification-service-1
```

Sau 30-40 giây (service khởi động) chạy lại lệnh `list_queues`: `messages` về **0** và email **xuất hiện** trong hộp thư Mailtrap.

**Lưu ý:** cần cấu hình SMTP (Mailtrap) trước. Nếu chưa, email gửi lỗi, thử lại 3 lần rồi vào hàng đợi lỗi `notification.events.dlq`. Đơn hàng vẫn đặt được bình thường.

Có thể làm tương tự với các service độc lập khác, ví dụ `docker stop deploy-product-service-1`: xem sản phẩm lỗi nhưng đăng nhập vẫn chạy.

---

## 7. Demo 6: bảo mật và phân quyền (2 phút)

Dùng Swagger hoặc Postman (folder **6. Security checks**):

| Thử | Kết quả |
|---|---|
| Gọi `GET /api/orders` không có token | **401** |
| Khách hàng gọi `PUT /api/inventory/{sku}` | **403** (chỉ ADMIN) |
| Khách hàng tạo sản phẩm | **403** |
| Gửi kèm header giả `X-User-Id`, `X-User-Roles: ADMIN` | bị **bỏ qua**, vẫn là người dùng thật của token |
| Xem đơn hàng của người khác | **404** (không lộ sự tồn tại) |
| Gọi webhook thanh toán với chữ ký sai | bị **từ chối** |

---

## 8. Demo 7: HTTPS, tường lửa, giám sát (3 phút)

- **HTTPS:** mở https://api.cunghuynh.id.vn, bấm ổ khoá trên trình duyệt: chứng chỉ **Let's Encrypt** (Certbot, tự gia hạn). Vào `http://...` sẽ tự chuyển sang `https://`.
- **Tường lửa:** AWS Console -> EC2 -> Security Groups: chỉ mở **22 (chỉ IP của bạn), 80, 443**. Trên server: `sudo ufw status`.
- **Metrics:** Grafana -> Dashboards -> New -> Import -> nhập ID **4701** (JVM) hoặc **12900** (Spring Boot), chọn nguồn dữ liệu *Prometheus*. Dashboard chưa được cấu hình sẵn, nên hãy import trước giờ demo.
- **Log tập trung:** Grafana -> Explore -> nguồn *Loki* -> truy vấn `{service="order-inventory-service"}` (nếu không thấy log, thử nhãn `container` thay cho `service`; phần này tôi chưa kiểm chứng trên server, hãy thử trước giờ demo).
- **Các target:** Prometheus đang thu thập từ 6 service + RabbitMQ.

---

## 9. Chạy tự động bằng Postman (tuỳ chọn, 2 phút)

Import `docs/postman/` (collection + environment production). Chọn environment, điền `adminPassword`, bấm **Run collection**: 64 request chạy theo thứ tự, có kiểm tra kết quả (saga, bù trừ, phân quyền...). Xem [docs/postman/README.md](docs/postman/README.md).

---

## 10. Sau buổi demo: dọn dẹp

```bash
cd ~/omnichannel/deploy
sed -i 's/^SWAGGER_ENABLED=.*/SWAGGER_ENABLED=false/' .env      # tắt Swagger công khai
sed -i 's/^RATE_LIMIT_RPM=.*/RATE_LIMIT_RPM=120/' .env
# bỏ dòng COMPOSE_PROFILES=monitoring trong .env nếu không muốn Grafana công khai
docker compose -f docker-compose.production.yml up -d
```

Tiết kiệm credit AWS: EC2 Console -> Instance state -> **Stop**. Kết thúc khoá học: **Terminate** instance, xoá volume, **giải phóng Elastic IP**.

---

## 11. Xử lý sự cố khi demo

| Hiện tượng | Cách xử lý nhanh |
|---|---|
| Trang trả `502 Bad Gateway` | service đang khởi động (mỗi service 30-60 giây). `docker compose -f docker-compose.production.yml ps` |
| Đơn hàng mãi ở `PENDING` sau khi thanh toán | đợi thêm 3-5 giây rồi tải lại. Nếu vẫn kẹt: `docker compose ... logs --tail=50 order-inventory-service payment-service` |
| `409 Insufficient stock` | hết hàng thật. Admin đặt lại: `PUT /api/inventory/{sku}` `{"available": 10}` |
| Email không đến | chưa cấu hình `MAIL_USER/MAIL_PASSWORD`, hoặc service Notification đang tắt (xem Demo 5) |
| `401` khi gọi API | token hết hạn (15 phút). Đăng nhập lại và **Authorize** |
| `429 Too many requests` | vượt giới hạn 120 request/phút/IP. Nghỉ 1 phút, hoặc xem Demo 4 để nâng tạm |
| Swagger không mở được | `SWAGGER_ENABLED` chưa là `true`, hoặc chưa chạy `up -d api-gateway` |
| Server chậm, container bị `Killed` | hết RAM: `free -h`. Tắt Grafana (xoá `COMPOSE_PROFILES`) |
| Không vào được SSH | IP của bạn đổi: sửa rule SSH trong Security Group sang "My IP" |

---

## 12. Câu hỏi hay gặp khi bảo vệ (gợi ý trả lời)

- **Tại sao dùng cả Redis lẫn PostgreSQL để trừ kho?** Redis (Lua nguyên tử) chặn sớm lượng request thừa với độ trễ thấp. PostgreSQL mới là nguồn sự thật: câu lệnh `UPDATE ... WHERE available >= n` đảm bảo không bao giờ âm kho, kể cả khi Redis lỗi (khi đó chỉ bỏ qua lớp Redis).
- **Outbox pattern để làm gì?** Sự kiện được ghi **cùng giao dịch DB** với đơn hàng, rồi mới được gửi sang RabbitMQ. RabbitMQ chết thì sự kiện chỉ chậm chứ không mất. Consumer idempotent nên giao nhận *ít nhất một lần* vẫn an toàn.
- **Tại sao Saga kiểu choreography?** Các service không biết nhau, chỉ phản ứng với sự kiện. Đơn giản, ít điểm nghẽn. Nhược điểm là khó theo dõi toàn cục (giải quyết bằng log tập trung và tracing).
- **Nếu thanh toán thành công nhưng đơn đã bị huỷ do quá 15 phút?** Hệ thống ghi log và bỏ qua sự kiện; hệ thống thật cần tự động hoàn tiền (ghi trong phần hạn chế).
- **Làm sao biết webhook thanh toán là thật?** Kiểm tra chữ ký HMAC (SHA-512 với VNPay, SHA-256 với MoMo) bằng so sánh thời gian hằng số; kiểm tra số tiền; xử lý idempotent.
- **Vì sao gateway xoá header `X-User-*`?** Các service phía sau tin vào header này. Nếu không xoá, client có thể giả mạo `X-User-Roles: ADMIN`. Đã có unit test cho việc này.
- **Hạn chế hiện tại?** Chưa thử với VNPay/MoMo sandbox thật; hoàn tiền mới ghi nhận trạng thái chứ chưa gọi API của cổng; chưa có dashboard Grafana dựng sẵn; rate limit lưu trong bộ nhớ (một gateway); thông lượng đo được ~380 req/s trên máy local.
