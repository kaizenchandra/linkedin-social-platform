package dev.network.member.connection;

import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface ConnectionRepository extends JpaRepository<Connection, String> {
  Optional<Connection> findByLowIdAndHighId(String low, String high);

  @Query(
      "select count(c) from Connection c where (c.lowId=:id or c.highId=:id) and"
          + " c.state=dev.network.member.connection.Connection.State.ACCEPTED")
  long degree(String id);

  @Query(
      "select c from Connection c where (c.lowId=:id or c.highId=:id) and c.state=:state order by"
          + " c.updatedAt desc,c.id desc")
  List<Connection> list(String id, Connection.State state, Pageable pageable);
}
