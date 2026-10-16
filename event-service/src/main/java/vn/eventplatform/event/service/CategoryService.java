package vn.eventplatform.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.event.domain.Category;
import vn.eventplatform.event.domain.CategoryRequest;
import vn.eventplatform.event.domain.enums.CategoryStatus;
import vn.eventplatform.event.domain.enums.ReviewStatus;
import vn.eventplatform.event.dto.CategoryDto;
import vn.eventplatform.event.mapper.EventMapper;
import vn.eventplatform.event.repository.CategoryRepository;
import vn.eventplatform.event.repository.CategoryRequestRepository;
import vn.eventplatform.event.repository.EventRepository;
import vn.eventplatform.event.util.NormalizationUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final List<CategoryStatus> ACTIVE_OR_DRAFT_STATUSES = List.of(
            CategoryStatus.DRAFT,
            CategoryStatus.PUBLISHED
    );

    private final CategoryRepository categoryRepository;
    private final CategoryRequestRepository categoryRequestRepository;
    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    @Transactional
    public CategoryDto.CategoryResponse createCategory(CategoryDto.CreateCategoryRequest request) {
        String cleanName = NormalizationUtils.cleanDisplayText(request.getName());
        String normalizedName = NormalizationUtils.normalizeForComparison(cleanName);
        if (cleanName.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tên danh mục không hợp lệ");
        }
        if (categoryRepository.existsByNormalizedNameAndStatusIn(normalizedName, ACTIVE_OR_DRAFT_STATUSES)) {
            throw new AppException(ErrorCode.CONFLICT, "Danh mục '" + cleanName + "' đã tồn tại");
        }

        String slug = generateUniqueCategorySlug(cleanName, null);
        Category category = Category.builder()
                .name(cleanName)
                .normalizedName(normalizedName)
                .slug(slug)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .status(request.getStatus() != null ? request.getStatus() : CategoryStatus.PUBLISHED)
                .build();

        try {
            category = categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(ErrorCode.CONFLICT, "Danh mục '" + cleanName + "' đã tồn tại");
        }
        return eventMapper.toCategoryResponse(category);
    }

    @Transactional
    public CategoryDto.CategoryResponse updateCategory(UUID id, CategoryDto.UpdateCategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy danh mục"));

        String cleanName = NormalizationUtils.cleanDisplayText(request.getName());
        String normalizedName = NormalizationUtils.normalizeForComparison(cleanName);
        if (cleanName.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tên danh mục không hợp lệ");
        }
        if (categoryRepository.existsByNormalizedNameAndStatusInAndIdNot(normalizedName, ACTIVE_OR_DRAFT_STATUSES, id)) {
            throw new AppException(ErrorCode.CONFLICT, "Danh mục '" + cleanName + "' đã tồn tại");
        }

        category.setName(cleanName);
        category.setNormalizedName(normalizedName);
        category.setSlug(generateUniqueCategorySlug(cleanName, id));
        category.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);

        try {
            category = categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(ErrorCode.CONFLICT, "Danh mục '" + cleanName + "' đã tồn tại");
        }
        return eventMapper.toCategoryResponse(category);
    }

    @Transactional
    public CategoryDto.CategoryResponse updateCategoryStatus(UUID id, CategoryDto.UpdateCategoryStatusRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy danh mục"));
        category.setStatus(request.getStatus());
        return eventMapper.toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy danh mục"));
        if (eventRepository.existsByCategoryId(id)) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Không thể xóa danh mục đang có sự kiện sử dụng. Vui lòng chuyển trạng thái sang ARCHIVED");
        }
        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryDto.CategoryResponse> listCategories(CategoryStatus status, boolean isAdmin) {
        List<Category> categories;
        if (status != null) {
            categories = categoryRepository.findByStatusOrderBySortOrderAscNameAsc(status);
        } else if (isAdmin) {
            categories = categoryRepository.findAllByOrderBySortOrderAscNameAsc();
        } else {
            categories = categoryRepository.findByStatusOrderBySortOrderAscNameAsc(CategoryStatus.PUBLISHED);
        }
        return categories.stream().map(eventMapper::toCategoryResponse).toList();
    }

    @Transactional(readOnly = true)
    public CategoryDto.CategoryResponse getCategoryByIdOrSlug(String idOrSlug) {
        Category category = null;
        try {
            UUID uuid = UUID.fromString(idOrSlug);
            category = categoryRepository.findById(uuid).orElse(null);
        } catch (IllegalArgumentException ignored) {
        }
        if (category == null) {
            category = categoryRepository.findBySlug(idOrSlug)
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy danh mục"));
        }
        return eventMapper.toCategoryResponse(category);
    }

    @Transactional
    public CategoryDto.CategoryProposalResponse createProposal(
            UUID organizerId,
            CategoryDto.CreateCategoryProposalRequest request
    ) {
        String cleanName = NormalizationUtils.cleanDisplayText(request.getRequestedName());
        String normalizedName = NormalizationUtils.normalizeForComparison(cleanName);
        if (cleanName.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tên danh mục đề xuất không hợp lệ");
        }
        if (categoryRepository.existsByNormalizedNameAndStatusIn(normalizedName, ACTIVE_OR_DRAFT_STATUSES)) {
            throw new AppException(ErrorCode.CONFLICT, "Danh mục '" + cleanName + "' đã tồn tại trên hệ thống");
        }
        if (categoryRequestRepository.existsByNormalizedProposedNameAndStatus(normalizedName, ReviewStatus.PENDING)) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Đã có yêu cầu đề xuất danh mục '" + cleanName + "' đang chờ phê duyệt");
        }

        CategoryRequest proposal = CategoryRequest.builder()
                .requesterId(organizerId)
                .proposedName(cleanName)
                .normalizedProposedName(normalizedName)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .status(ReviewStatus.PENDING)
                .build();

        try {
            proposal = categoryRequestRepository.saveAndFlush(proposal);
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Đã có yêu cầu đề xuất danh mục '" + cleanName + "' đang chờ phê duyệt");
        }
        return eventMapper.toCategoryProposalResponse(proposal);
    }

    @Transactional(readOnly = true)
    public Page<CategoryDto.CategoryProposalResponse> listProposals(
            ReviewStatus status,
            UUID organizerId,
            boolean isAdmin,
            Pageable pageable
    ) {
        Page<CategoryRequest> page;
        if (isAdmin) {
            page = (status != null)
                    ? categoryRequestRepository.findByStatusOrderByCreatedAtAsc(status, pageable)
                    : categoryRequestRepository.findAll(pageable);
        } else {
            page = categoryRequestRepository.findByRequesterId(organizerId, pageable);
        }
        return page.map(eventMapper::toCategoryProposalResponse);
    }

    @Transactional
    public CategoryDto.CategoryProposalResponse approveProposal(UUID proposalId, UUID adminId) {
        CategoryRequest proposal = categoryRequestRepository.findById(proposalId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy yêu cầu đề xuất danh mục"));
        if (proposal.getStatus() != ReviewStatus.PENDING) {
            throw new AppException(ErrorCode.CONFLICT, "Yêu cầu đề xuất danh mục này đã được xử lý trước đó");
        }
        if (categoryRepository.existsByNormalizedNameAndStatusIn(proposal.getNormalizedProposedName(), ACTIVE_OR_DRAFT_STATUSES)) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Danh mục '" + proposal.getProposedName() + "' đã tồn tại trên hệ thống");
        }

        Category category = Category.builder()
                .name(proposal.getProposedName())
                .normalizedName(proposal.getNormalizedProposedName())
                .slug(generateUniqueCategorySlug(proposal.getProposedName(), null))
                .description(proposal.getDescription())
                .status(CategoryStatus.PUBLISHED)
                .build();
        category = categoryRepository.saveAndFlush(category);

        proposal.setStatus(ReviewStatus.APPROVED);
        proposal.setReviewedBy(adminId);
        proposal.setReviewedAt(Instant.now());
        proposal.setCategoryId(category.getId());

        return eventMapper.toCategoryProposalResponse(categoryRequestRepository.save(proposal));
    }

    @Transactional
    public CategoryDto.CategoryProposalResponse rejectProposal(
            UUID proposalId,
            UUID adminId,
            CategoryDto.RejectCategoryProposalRequest request
    ) {
        CategoryRequest proposal = categoryRequestRepository.findById(proposalId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy yêu cầu đề xuất danh mục"));
        if (proposal.getStatus() != ReviewStatus.PENDING) {
            throw new AppException(ErrorCode.CONFLICT, "Yêu cầu đề xuất danh mục này đã được xử lý trước đó");
        }

        proposal.setStatus(ReviewStatus.REJECTED);
        proposal.setReviewNote(request.getRejectionReason().trim());
        proposal.setReviewedBy(adminId);
        proposal.setReviewedAt(Instant.now());

        return eventMapper.toCategoryProposalResponse(categoryRequestRepository.save(proposal));
    }

    private String generateUniqueCategorySlug(String name, UUID excludeId) {
        String baseSlug = NormalizationUtils.toSlug(name);
        String candidate = baseSlug;
        int counter = 1;
        while (true) {
            boolean exists = (excludeId == null)
                    ? categoryRepository.existsBySlug(candidate)
                    : categoryRepository.existsBySlugAndIdNot(candidate, excludeId);
            if (!exists) {
                return candidate;
            }
            counter++;
            candidate = baseSlug + "-" + counter;
        }
    }
}
