package org.cdpg.dx.aaa.organization.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.dao.OrganizationUserDAO;
import org.cdpg.dx.aaa.organization.models.DeptAdminScope;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.aaa.organization.service.OrganizationHierarchyService;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeptAdminAccessHandlerTest {

  @Mock private OrganizationUserDAO organizationUserDAO;
  @Mock private OrganizationHierarchyService hierarchyService;
  @Mock private RoutingContext ctx;
  @Mock private Handler<RoutingContext> fallback;

  private DeptAdminAccessHandler guard;

  private final UUID orgId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID adminNode = UUID.randomUUID();
  private final UUID childNode = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    guard = new DeptAdminAccessHandler(organizationUserDAO, hierarchyService);
  }

  private void givenPrincipal(JsonObject extraClaims) {
    JsonObject principal =
        new JsonObject()
            .put("sub", userId.toString())
            .put("scopes", new JsonArray().add("data-access"))
            .mergeIn(extraClaims);
    when(ctx.user()).thenReturn(User.create(principal));
  }

  private OrganizationUser member(Role role, UUID adminOfNodeId) {
    return new OrganizationUser(
        UUID.randomUUID(), orgId, userId, "name", role, "title", "emp", null, "a@b.c",
        adminNode, adminOfNodeId, null, null);
  }

  @Test
  @DisplayName("a dept_admin passes with their department subtree published")
  void deptAdminPassesWithScope() {
    givenPrincipal(new JsonObject().put("organisation_id", orgId.toString()));
    when(organizationUserDAO.getByOrganizationIdAndUserId(orgId, userId))
        .thenReturn(Future.succeededFuture(member(Role.DEPT_ADMIN, adminNode)));
    when(hierarchyService.getDescendantNodeIds(orgId, adminNode))
        .thenReturn(Future.succeededFuture(List.of(adminNode, childNode)));

    guard.orElse(fallback).handle(ctx);

    ArgumentCaptor<DeptAdminScope> scope = ArgumentCaptor.forClass(DeptAdminScope.class);
    verify(ctx).put(eq(DeptAdminAccessHandler.DEPT_ADMIN_SCOPE_KEY), scope.capture());
    verify(ctx).next();
    verifyNoInteractions(fallback);
    assertThat(scope.getValue().organizationId()).isEqualTo(orgId);
    assertThat(scope.getValue().nodeIds()).containsExactly(adminNode, childNode);
  }

  @Test
  @DisplayName("a plain member falls through to the route's existing guard")
  void memberFallsThrough() {
    givenPrincipal(new JsonObject().put("organisation_id", orgId.toString()));
    when(organizationUserDAO.getByOrganizationIdAndUserId(orgId, userId))
        .thenReturn(Future.succeededFuture(member(Role.USER, null)));

    guard.orElse(fallback).handle(ctx);

    verify(fallback).handle(ctx);
    verify(ctx, never()).next();
    verify(ctx, never()).put(any(), any());
  }

  @Test
  @DisplayName("a caller with no organisation falls through without a membership lookup")
  void noOrganisationFallsThrough() {
    givenPrincipal(new JsonObject());

    guard.orElse(fallback).handle(ctx);

    verify(fallback).handle(ctx);
    verifyNoInteractions(organizationUserDAO, hierarchyService);
  }

  @Test
  @DisplayName("a delegated principal never resolves as dept_admin")
  void delegateFallsThrough() {
    givenPrincipal(
        new JsonObject()
            .put("organisation_id", orgId.toString())
            .put("delegatee_sub", UUID.randomUUID().toString()));

    guard.orElse(fallback).handle(ctx);

    verify(fallback).handle(ctx);
    verifyNoInteractions(organizationUserDAO, hierarchyService);
  }

  @Test
  @DisplayName("a failed membership lookup fails the request instead of falling through")
  void lookupFailureFailsClosed() {
    givenPrincipal(new JsonObject().put("organisation_id", orgId.toString()));
    RuntimeException dbError = new RuntimeException("db down");
    when(organizationUserDAO.getByOrganizationIdAndUserId(orgId, userId))
        .thenReturn(Future.failedFuture(dbError));

    guard.orElse(fallback).handle(ctx);

    verify(ctx).fail(dbError);
    verify(ctx, never()).next();
    verifyNoInteractions(fallback);
  }

  @Test
  @DisplayName("the dept_admin-only guard rejects everyone who is not a dept_admin")
  void requiredRejectsNonDeptAdmin() {
    givenPrincipal(new JsonObject().put("organisation_id", orgId.toString()));
    when(organizationUserDAO.getByOrganizationIdAndUserId(orgId, userId))
        .thenReturn(Future.succeededFuture(member(Role.ADMIN, null)));

    guard.required().handle(ctx);

    verify(ctx).fail(any(DxForbiddenException.class));
    verify(ctx, never()).next();
  }

  @Test
  @DisplayName("strict coverage excludes the administered department itself")
  void strictCoverageExcludesOwnDepartment() {
    DeptAdminScope scope =
        new DeptAdminScope(orgId, userId, adminNode, List.of(adminNode, childNode));

    assertThat(scope.covers(adminNode)).isTrue();
    assertThat(scope.covers(childNode)).isTrue();
    assertThat(scope.covers(UUID.randomUUID())).isFalse();
    assertThat(scope.covers(null)).isFalse();
    assertThat(scope.coversStrictly(adminNode)).isFalse();
    assertThat(scope.coversStrictly(childNode)).isTrue();
    assertThat(scope.strictNodeIdStrings()).containsExactly(childNode.toString());
  }
}
