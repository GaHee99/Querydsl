package study.querydsl;

import static study.querydsl.entity.QMember.*;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {
        queryFactory = new JPAQueryFactory(em); // 필드레벨로 가져가도 괜찮다. 동시성 문제도 해결이 된다.
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
    }

    @Test
    public void startJPQL() {
        String qlString =
                "select m from Member m " +
                "where m.username = :username";

        // member1을 찾아라
        Member findMember = em.createQuery(qlString, Member.class)
                .setParameter("username" , "member1")
                .getSingleResult();

        Assertions.assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void startQuerydsl() {
        QMember m1 = new QMember("m1"); // JPQL에 m1이 먹힌다, 같은 테이블을 join해서 사용하는 경우에만 QMember 선언
        Member findMember = queryFactory
                                        .select(member) //static import 권장
                                        .from(member)
                                        .where(member.username.eq("member1")) // 파라미터 바인딩 처리
                                        .fetchOne();

        Assertions.assertThat(findMember.getUsername()).isEqualTo("member1");
    }




}
