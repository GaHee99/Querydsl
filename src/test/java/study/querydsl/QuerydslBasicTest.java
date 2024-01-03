package study.querydsl;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.team;

import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
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
         * -> deprecate됨, 3.0 pdf참고~
         */
    }

    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순
     * 2. 회원 이름 올림차순
     * 단 2에서 회원 이름이 없다면 마지막에 출력(nulls last)
     */
    @Test
    public void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory.selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast()) //nullsFirst()가능
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();

            /* select
                member1
            from
                Member member1
            where
                member1.age = ?1
            order by
                member1.age desc,
                member1.username asc nulls last */
    }

    @Test
    public void paging1() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1) //0부터 시작
                .limit(2)
                .fetch();

        assertThat(result.size()).isEqualTo(2);


         /* select
        m1_0.member_id,
                m1_0.age,
                m1_0.team_id,
                m1_0.username
        from
        member m1_0
        order by
        m1_0.username desc
        offset
                ? rows
        fetch
        first ? rows only
            */
    }

    @Test
    public void paging2() {
        QueryResults<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1) //0부터 시작
                .limit(2)
                .fetchResults();

        assertThat(result.getTotal()).isEqualTo(4);
        assertThat(result.getLimit()).isEqualTo(2);
        assertThat(result.getOffset()).isEqualTo(1);
        assertThat(result.getResults().size()).isEqualTo(2);

        // fetchResults는 count쿼리 나가고, content쿼리 나간다

//        select
//        m1_0.member_id,
//                m1_0.age,
//                m1_0.team_id,
//                m1_0.username
//        from
//        member m1_0
//        order by
//        m1_0.username desc
//        offset
//                ?
    }


    // 집합
    @Test
    public void aggregation() {
        // Tuple => 쿼리 dsl 튜플
        List<Tuple> result = queryFactory
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();

        Tuple tuple = result.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);

        /* select
        count(member1),
        sum(member1.age),
        avg(member1.age),
        max(member1.age),
        min(member1.age)
    from
        Member member1 */
    }

    /**
     * 팀 이름과 각 팀의 평균 연령을 구해라.
     */
    @Test
    public void group() throws Exception{
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
//                .having() 도 가능
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);

        /* select
        team.name,
        avg(member1.age)
    from
        Member member1
    inner join
        member1.team as team
    group by
        team.name */
    }


    /**
     * 팀 A에 소속된 모든 회원
     */
    @Test
    public void join() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .leftJoin(member.team, team) // member와 team을 join, team은 별칭
                .where(team.name.eq("teamA"))
                .fetch();

        Assertions.assertThat(result)
                .extracting("username")
                .containsExactly("member1","member2");

        // inner join
        /* select member1
            from Member member1
              inner join member1.team as team
            where team.name = ?1 */

        // left join
        /* select member1
        from Member member1
          left join member1.team as team
        where team.name = ?1 */
    }

    /**
     * 세타 조인 : from절에 나열
     * 회원의 이름이 팀 이름과 같은 회원 조회
     * 주의 : 외부 조인 불가능!
     */
    @Test void theta_join() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Member> result = queryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        Assertions.assertThat(result)
                .extracting("username")
                .containsExactly("teamA","teamB");

        /* select
        member1
        from
            Member member1,
            Team team
        where
        member1.username = team.name */
    }



}
