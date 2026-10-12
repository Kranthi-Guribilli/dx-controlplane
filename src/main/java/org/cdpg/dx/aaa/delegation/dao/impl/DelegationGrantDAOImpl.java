package org.cdpg.dx.aaa.delegation.dao.impl;

import static org.cdpg.dx.aaa.delegation.util.Constants.*;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_STATUS;
import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.delegation.dao.DelegationGrantDAO;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.aaa.delegation.util.Status;
import org.cdpg.dx.common.exception.BaseDxException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.TemporalRequest;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.database.postgres.models.QueryResult;
import org.cdpg.dx.database.postgres.models.UpdateQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.database.postgres.util.ConditionBuilder;

public class DelegationGrantDAOImpl extends AbstractBaseDAO<DelegationGrant> implements DelegationGrantDAO {

  private static final Logger LOGGER = LogManager.getLogger(DelegationGrantDAOImpl.class);

  public DelegationGrantDAOImpl(PostgresService postgresService) {
    super(postgresService, DELEGATION_GRANT_TABLE, DELEGATION_ID, DelegationGrant::fromJson);
  }

  /**
   * Expired is derived from {@code expiry_at} rather than stored (see {@link
   * DelegationGrant#effectiveStatus}), so a {@code status} filter of active or expired is rewritten
   * to match: active means stored active and not yet past expiry, expired means stored active and
   * past expiry.
   */
  @Override
  public Future<PaginatedResult<DelegationGrant>> getAllWithFilters(PaginatedRequest request) {
    Object statusFilter = request.filters() != null ? request.filters().get(STATUS) : null;
    LOGGER.debug(
        "Delegation list request - filters: {}, temporal: {}",
        request.filters(),
        request.temporalRequests());
    if (statusFilter == null) {
      return super.getAllWithFilters(request);
    }

    // The request builder passes query params as a list, e.g. status=[expired].
    if (statusFilter instanceof List<?> values) {
      if (values.size() != 1) {
        return super.getAllWithFilters(request);
      }
      statusFilter = values.get(0);
    }

    String status = String.valueOf(statusFilter);
    String timeRel;
    if (Status.ACTIVE.getStatus().equalsIgnoreCase(status)) {
      timeRel = "after";
    } else if (Status.EXPIRED.getStatus().equalsIgnoreCase(status)) {
      timeRel = "before";
    } else {
      return super.getAllWithFilters(request);
    }

    Map<String, Object> filters = new HashMap<>(request.filters());
    filters.put(STATUS, Status.ACTIVE.getStatus());

    List<TemporalRequest> temporalRequests =
        request.temporalRequests() != null
            ? new ArrayList<>(request.temporalRequests())
            : new ArrayList<>();
    temporalRequests.add(
        new TemporalRequest(EXPIRY_AT, timeRel, LocalDateTime.now().format(FORMATTER), null));

    return super.getAllWithFilters(
        new PaginatedRequest(
            request.page(),
            request.size(),
            filters,
            temporalRequests,
            request.orderByList(),
            request.fuzzyFilters()));
  }

  @Override
  public Future<JsonArray> findActiveDelegationWithScopes(String delegatorId, String delegateeId) {
    String sql = """
        SELECT
          dg.delegation_id,
          dg.delegator_id,
          dg.delegate_id,
          dg.justification,
          dg.expiry_at   AS delegation_expiry_at,
          dg.status,
          dg.created_at,
          dg.revoked_at,
          dsc.role,
          dsc.scope,
          dsc.entity_id,
          dsc.entity_type,
          dsc.expiry_at  AS constraint_expiry_at
        FROM delegation_grants dg
        JOIN delegation_scope_constraints dsc ON dg.delegation_id = dsc.delegation_id
        WHERE dg.delegator_id = $1::uuid
          AND dg.delegate_id  = $2::uuid
          AND dg.status = 'active'
          AND (dg.expiry_at  IS NULL OR dg.expiry_at  > NOW())
          AND (dsc.expiry_at IS NULL OR dsc.expiry_at > NOW())
        """;

    JsonArray params = new JsonArray().add(delegatorId).add(delegateeId);

    return postgresService.executeQuery(sql, params)
        .map(result -> result.getRows())
        .recover(err -> Future.failedFuture(BaseDxException.from(err)));
  }

  @Override
  public Future<JsonArray> rejectDelegation(String delegationId, String delegateId) {

    String sql =
        """
        UPDATE delegation_grants
        SET status = 'rejected'
        WHERE delegation_id = $1::uuid
          AND delegate_id = $2::uuid
          AND status = 'active'
        RETURNING *
        """;

    JsonArray params = new JsonArray().add(delegationId).add(delegateId);

    return postgresService
        .executeQuery(sql, params)
        .map(QueryResult::getRows)
        .recover(
            err -> {
              LOGGER.error(
                  "Failed to reject delegation {} by delegate {}", delegationId, delegateId, err);

              return Future.failedFuture(BaseDxException.from(err));
            });
  }

  public Future<QueryResult> deactivate(UUID delegationId) {

    UpdateQuery query =
        new UpdateQuery()
            .setTable("delegation_grants")
            .setColumns(List.of(DB_STATUS))
            .setValues(List.of("deleted"))
            .setCondition(
                new Condition()
                    .setColumn("delegation_id")
                    .setValues(List.of(delegationId.toString()))
                    .setOperator(Condition.Operator.EQUALS));

    return postgresService.update(query);
  }
}
