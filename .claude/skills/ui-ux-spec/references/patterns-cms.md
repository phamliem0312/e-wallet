# Pattern — CMS back-office

Người dùng là nhân viên dùng CMS nhiều giờ mỗi ngày trên desktop. Mục tiêu: **tìm nhanh, đọc nhanh, khó thao tác nhầm, và mọi thao tác đều truy vết được.** Một cú click nhầm trong CMS có thể khoá ví hay điều chỉnh tiền của khách, nên an toàn quan trọng hơn tiện lợi.

## 1. Khung trang
- `Sidebar` trái (nhóm theo nghiệp vụ: Khách hàng, Giao dịch, Đối soát, Phê duyệt, Cấu hình, Báo cáo, Audit), `Breadcrumb` trên cùng, vùng nội dung rộng.
- **Banner môi trường** cố định trên cùng ở mọi môi trường không phải production ("STAGING — dữ liệu thử nghiệm", token `warning`), để không ai nhầm môi trường khi thao tác.
- Hiển thị người đang đăng nhập và role; menu chỉ hiện mục mà role được dùng.
- Thiết kế cho ≥ 1280 px; ở 1024 px vẫn dùng được (bảng cuộn ngang, không vỡ bố cục).

## 2. Bảng dữ liệu (DataTable)
- Phân trang, sắp xếp, lọc **phía server**. Mặc định sắp mới nhất lên đầu.
- **Bộ lọc phản ánh vào URL** (query string, không chứa dữ liệu nhạy cảm) để chia sẻ link và giữ trạng thái khi quay lại.
- Ô tìm kiếm theo trường chính xác (mã giao dịch, SĐT, mã khách); ghi rõ đang tìm theo trường nào.
- Header cố định khi cuộn; cột số tiền căn phải, `tabular-nums`; thời gian dạng `dd/MM/yyyy HH:mm:ss` theo giờ Việt Nam.
- Trạng thái dùng `StatusBadge` (chữ + màu + icon).
- ID dài dùng `CopyableId`: rút gọn, có nút sao chép, tooltip hiện đầy đủ.
- Trạng thái: đang tải dùng `Skeleton` theo hàng; không có kết quả thì hiện "Không có kết quả phù hợp" kèm nút xoá bộ lọc; lỗi tải hiện `Alert` kèm "Thử lại".
- Xuất CSV: theo bộ lọc hiện tại, có giới hạn số dòng, ghi audit; không xuất dữ liệu nhạy cảm chưa che.

## 3. Trang chi tiết
- Đầu trang: tóm tắt (tên/mã, trạng thái, các số quan trọng) và các nút hành động.
- Thân trang: `Tabs` (Thông tin · Giao dịch · Liên kết · Lịch sử thao tác/Audit · Ghi chú).
- Hiển thị liên kết chéo: từ giao dịch sang khách, sang lệnh ngân hàng, sang trace.

## 4. Thao tác có rủi ro (khoá ví, điều chỉnh tiền, hoàn tiền, đổi cấu hình)
1. **Maker–checker:** nút hành động mở form tạo **yêu cầu** chứ không thực thi ngay. Câu chữ nói đúng điều đó: "Gửi yêu cầu khoá ví", không phải "Khoá ví".
2. Form yêu cầu bắt buộc có **lý do** (danh sách lý do + mô tả); thao tác tiền phải nhập lại số tiền để khớp.
3. Bước cuối dùng `AlertDialog` tóm tắt: đối tượng, thay đổi trước → sau, người duyệt cần có role gì.
4. Sau khi gửi: banner "Đang chờ duyệt" trên đối tượng, kèm link tới yêu cầu; không cho tạo yêu cầu trùng khi yêu cầu cũ đang chờ.
5. **Màn hình duyệt:** hiển thị **so sánh trước/sau** (diff), người tạo, lý do, thời điểm, hạn duyệt. Nút "Duyệt" bị khoá kèm lý do hiển thị khi người xem chính là người tạo. Từ chối thì bắt buộc nhập lý do.
6. Không có thao tác hàng loạt cho tiền hay khoá ví.

## 5. Dữ liệu nhạy cảm & phân quyền
- Dữ liệu nhạy cảm (CCCD, SĐT, số tài khoản) **mặc định đã che** (`MaskedValue`).
- Nút "Hiện" chỉ có với role được phép: yêu cầu chọn lý do, ghi audit, và tự che lại sau một khoảng thời gian (theo cấu hình, ghi câu hỏi mở nếu SRS chưa có).
- **Ẩn hay khoá?** Ẩn hẳn chức năng mà role không bao giờ được dùng. Khoá (disabled) kèm lý do cho chức năng mà role được dùng nhưng điều kiện hiện tại chưa cho phép (ví dụ đang có yêu cầu chờ duyệt).
- Không hiển thị lỗi 403 thô: hiển thị "Bạn không có quyền xem nội dung này" và gợi ý liên hệ quản trị.

## 6. Hiệu suất làm việc
- Phím tắt cho thao tác lặp lại (`/` để focus ô tìm kiếm); thứ tự tab hợp lý.
- Giữ vị trí cuộn và bộ lọc khi quay lại từ trang chi tiết.
- Cập nhật trạng thái tự động (ví dụ hàng đợi duyệt) bằng polling nhẹ và thông báo `aria-live`, không tải lại cả trang.
