package study.querydsl;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static study.querydsl.entity.QMember.*;

import com.querydsl.core.QueryResults;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
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
           Member findMember = queryFactory
                                        .select(member) //static import 권장
                                        .from(member)
                                        .where(member.username.eq("member1")) // 파라미터 바인딩 처리
                                        .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void search() {
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1").and(member.age.eq(10)))
                .fetchOne();

        Member findMemberWithAge = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.between(10, 30)))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void searchAndParam() {
        Member findMember = queryFactory //쉼표로 and 가능
                .selectFrom(member)
                .where(
                        member.username.eq("member1"),
                        member.age.eq(10)
                )
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void resultFetch() {
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();

        Member fetchOne = queryFactory
                .selectFrom(member)
                .fetchOne();

        Member fetchFirst = queryFactory
                .selectFrom(member)
                .fetchFirst();// == .limit(1).fetchOne()과 통치


        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .fetchResults();

        results.getTotal(); // TotalCount를 가져와야 하므로, 쿼리가 두번 실행된다.
                            // select count(m1_0.member_id) from member m1_0 실행됨
                            // 페이징 하기 위한 totalCount를 가져온다.
        List<Member> content = results.getResults(); // 내용 가져오기

        long total = queryFactory       //select절을 count만 한다.
                .selectFrom(member)
                .fetchCount();

        /**
         * 페이징 쿼리가 복잡해지면 데이터(content)쿼리와 실제 total count를 갖고 온 결과값이
         * 성능 이슈로 다를 수 있다. -> 성능을 더 최적화 하기 위해서 total count쿼리를 최적화 해서 만든 경우가 있기 때문
         * 이런 상황에서는(복잡하고 성능이 중요한 페이징 쿼리) 위의 두개를 쓰면 안되고,
         * 쿼리 두개를 따로 날려야 한다.
         */
    }
}
