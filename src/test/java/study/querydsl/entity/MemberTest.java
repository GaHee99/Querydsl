package study.querydsl.entity;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
//@Commit  -> 다른 테스트들이랑 꼬일 위험이 크다.
class MemberTest {
    @Autowired
    EntityManager em;

    @Test
    public void testEntity() {
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);

        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member3", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);

        //초기화
        em.flush(); // 영속성 컨텍스트에 있는 object들을 DB에 반영
        em.clear(); // 영속성 컨텍스트 날라가고 DB초기화

        List<Member> members = em.createQuery("select m from Member m", Member.class)
                .getResultList();

        for(Member member : members) {
            System.out.println("member = " + member);
            System.out.println("-> member.team " + member.getTeam());
        }
    }
}