package org.cdpg.dx.common.util;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;

import io.vertx.core.Future;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.cdpg.dx.common.exception.BaseDxException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.database.postgres.util.ConditionBuilder;

/**
 * Expired is derived from {@code expiry_at} rather than stored: a row keeps its live status (e.g.
 * {@code GRANTED}, {@code ACTIVE}) after {@code expiry_at} passes, and is reported as expired when
 * read. A live row with no {@code expiry_at} never expires.
 */
public final class ExpiredStatusHelper {

  private ExpiredStatusHelper() {}

  /**
   * A status filter rewritten to match the derived status: {@code request} filters on the stored
   * live status, and {@code expiryCondition} must be ANDed with the request's own conditions.
   */
  public record StatusFilter(PaginatedRequest request, Condition expiryCondition) {}

  /** Returns {@code expiredStatus} if a row stored as {@code liveStatus} is past its expiry. */
  public static String effectiveStatus(
      String status, LocalDateTime expiryAt, String liveStatus, String expiredStatus) {
    if (liveStatus.equalsIgnoreCase(status)
        && expiryAt != null
        && !expiryAt.isAfter(LocalDateTime.now())) {
      return expiredStatus;
    }
    return status;
  }

  /**
   * Rewrites a single-valued {@code statusKey} filter of {@code liveStatus} or {@code
   * expiredStatus}: live means stored live and expiry_at after now or null, expired means stored
   * live and expiry_at before now. Returns null if the request needs no rewrite.
   */
  public static StatusFilter rewriteStatusFilter(
      PaginatedRequest request,
      String statusKey,
      String expiryKey,
      String liveStatus,
      String expiredStatus) {
    Object statusFilter = request.filters() != null ? request.filters().get(statusKey) : null;
    if (statusFilter == null) {
      return null;
    }

    // The request builder passes query params as a list, e.g. status=[EXPIRED].
    if (statusFilter instanceof List<?> values) {
      if (values.size() != 1) {
        return null;
      }
      statusFilter = values.get(0);
    }

    String status = String.valueOf(statusFilter);
    String now = LocalDateTime.now().format(FORMATTER);
    Condition expiryCondition;
    if (liveStatus.equalsIgnoreCase(status)) {
      expiryCondition =
          new Condition(
              List.of(
                  new Condition(expiryKey, Condition.Operator.GREATER, List.of(now)),
                  new Condition(expiryKey, Condition.Operator.IS_NULL, List.of())),
              Condition.LogicalOperator.OR);
    } else if (expiredStatus.equalsIgnoreCase(status)) {
      expiryCondition = new Condition(expiryKey, Condition.Operator.LESS, List.of(now));
    } else {
      return null;
    }

    Map<String, Object> filters = new HashMap<>(request.filters());
    filters.put(statusKey, liveStatus);

    return new StatusFilter(
        new PaginatedRequest(
            request.page(),
            request.size(),
            filters,
            request.temporalRequests(),
            request.orderByList(),
            request.fuzzyFilters()),
        expiryCondition);
  }

  /** ANDs two conditions, either of which may be null. */
  public static Condition and(Condition first, Condition second) {
    if (first == null) {
      return second;
    }
    if (second == null) {
      return first;
    }
    return new Condition(List.of(first, second), Condition.LogicalOperator.AND);
  }

  /**
   * Same paged select as {@link AbstractBaseDAO#getAllWithFilters(PaginatedRequest)}, with the
   * rewritten filter's expiry condition ANDed in.
   */
  public static <T extends BaseEntity<T>> Future<PaginatedResult<T>> getAllWithStatusFilter(
      AbstractBaseDAO<T> dao,
      PostgresService postgresService,
      String tableName,
      StatusFilter statusFilter) {
    PaginatedRequest request = statusFilter.request();
    int page = request.page() > 0 ? request.page() : 1;
    int size = request.size() > 0 ? request.size() : 10;
    int offset = (page - 1) * size;

    Condition condition =
        and(
            ConditionBuilder.fromFilters(
                request.filters(), request.temporalRequests(), request.fuzzyFilters()),
            statusFilter.expiryCondition());

    SelectQuery query =
        new SelectQuery(
            tableName, List.of("*"), condition, null, request.orderByList(), size, offset);

    return postgresService
        .select(query, true)
        .map(result -> dao.toPaginatedResult(result, page, size))
        .recover(err -> Future.failedFuture(BaseDxException.from(err)));
  }
}
