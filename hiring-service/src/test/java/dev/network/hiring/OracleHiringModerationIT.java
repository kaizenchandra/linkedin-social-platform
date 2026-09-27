package dev.network.hiring;
import static org.assertj.core.api.Assertions.*;
import dev.network.hiring.job.*;
import dev.network.hiring.moderation.HiringModeration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

class OracleHiringModerationIT extends OracleHiringTest {
 @Autowired HiringModeration moderation;
 @Autowired JobService jobs;
 @Test @WithMockUser(roles="moderator")
 void hiddenAndRestoredPreserveLifecycleAndReportsArePrivate() {
  String owner=id(),reporter=id();var c=companies.create(owner,input("moderate-"+id()));
  var j=jobs.create(owner,c.id(),new JobService.Input("Job","Description","Local",Job.Work.REMOTE,Job.Employment.FULL_TIME,null,null,null,null,null,null));
  jobs.transition(owner,j.id(),"publish");
  var r=moderation.submit(reporter,j.id(),HiringModeration.Reason.SUSPECTED_FRAUD,null);
  assertThat(moderation.submit(reporter,j.id(),HiringModeration.Reason.SPAM,null).id()).isEqualTo(r.id());
  assertThat(moderation.own(owner,null,20).items()).isEmpty();
  moderation.inspect(owner,r.id(),"Investigate report");
  moderation.act(owner,r.id(),HiringModeration.Action.HIDE,"Investigation");
  assertThat(status(()->jobs.get(j.id()))).isEqualTo(404);
  assertThat(status(()->moderation.submit(id(),j.id(),HiringModeration.Reason.SPAM,null))).isEqualTo(404);
  jobs.transition(owner,j.id(),"close");
  moderation.act(owner,r.id(),HiringModeration.Action.RESTORE,"Review completed");
  assertThat(jobs.managed(owner,c.id(),j.id()).state()).isEqualTo(Job.State.CLOSED);
  assertThat(status(()->jobs.get(j.id()))).isEqualTo(404);
  moderation.act(owner,r.id(),HiringModeration.Action.DISMISS,"Resolved");
  assertThat(moderation.history(r.id(),0,20)).hasSize(4);
 }
 @Test @WithMockUser
 void ordinaryMemberCannotUseModerationService() {
  assertThatThrownBy(()->moderation.queue(null,20)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
 }
}
