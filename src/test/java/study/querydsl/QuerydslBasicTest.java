package study.querydsl;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.team;

import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
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


    /**
     * 회원과 팀을 조인 하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * JPQL: select m, t Member m left join m.team on t.name = 'teamA'
     */
    @Test
    public void join_on_filtering() {
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team)
                .on(team.name.eq("teamA"))
                .fetch();

        for(Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }

        // 위의 쿼리가 inner join 일 경우 같은 결과를 나타내는 query
        List<Tuple> sameResult = queryFactory
                .select(member, team)
                .from(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();


        /*
        결과 값
        tuple = [Member(id=1, username=member1, age=10), Team(id=1, name=teamA)]
        tuple = [Member(id=2, username=member2, age=20), Team(id=1, name=teamA)]
        tuple = [Member(id=3, username=member3, age=30), null]
        tuple = [Member(id=4, username=member3, age=40), null]
         */


        /* select member1, team
        from Member member1
          left join member1.team as team with team.name = ?1 */
    }


    /**
     * 연관관계 없는 엔티티 외부 조인
     * 회원의 이름이 팀 이름과 같은 대상 외부 조인
     * theta조인과 다르게 외부 조인 가능
     */
    @Test void join_on_no_relation() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name)) // 이 부분이 차이점,
                                                            // 회원의 이름이 팀 이름과 같은 대상만 filtering
                .fetch();


        for(Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }

        //결과값
//        tuple = [Member(id=1, username=member1, age=10), null]
//        tuple = [Member(id=2, username=member2, age=20), null]
//        tuple = [Member(id=3, username=member3, age=30), null]
//        tuple = [Member(id=4, username=member3, age=40), null]
//        tuple = [Member(id=5, username=teamA, age=0), Team(id=1, name=teamA)]
//        tuple = [Member(id=6, username=teamB, age=0), Team(id=2, name=teamB)]
//        tuple = [Member(id=7, username=teamC, age=0), null]


        // JPQL
        /* select
        member1,
        team
       from
        Member member1,
        Team team */

        //SQL
       /* select
        m1_0.member_id,
                m1_0.age,
                m1_0.team_id,
                m1_0.username,
                t1_0.team_id,
                t1_0.name
        from
        member m1_0
        left join
        team t1_0
        on m1_0.username=t1_0.name --이 부분이 중요--
        */
    }


    @PersistenceUnit //entityManager를 만드는 factory
    EntityManagerFactory emf;

    //TIP: 페치 조인의 경우, 영속성 컨텍스트에 남아 있는 것들을 지워주지 않으면 결과를 제대로 보기 힘들다.
    @Test
    public void fetchJoinNo() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne(); //member만 조회

        // 이미 로딩이 된 엔티티인지, 로딩이 안 된 엔티티인지 알 수 있다.
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isFalse();

    }


    //TIP: 페치 조인의 경우, 영속성 컨텍스트에 남아 있는 것들을 지워주지 않으면 결과를 제대로 보기 힘들다.
    @Test
    public void fetchJoinUse() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin() //.fetchJoin()만 넣어주면 된다.
                .where(member.username.eq("member1"))
                .fetchOne(); //member만 조회

        // 이미 로딩이 된 엔티티인지, 로딩이 안 된 엔티티인지 알 수 있다.
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 적용").isTrue();
    }


    /**
     * 나이가 가장 많은 회원을 조회
     * subQuery이므로 밖에 있는 alias와 겹치면 안된다.
     */
    @Test
    public void subQuery() {

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();

        Assertions.assertThat(result).extracting("age")
                .containsExactly(40);

        /* select member1
        from Member member1
        where member1.age = (select max(memberSub.age)
        from Member memberSub) */
    }

    /**
     * 나이가 평균 이상인 회원 조회
     */
    @Test
    public void subQueyryGoe() {

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();

        Assertions.assertThat(result).extracting("age")
                .containsExactly(30, 40);

        /* select member1
        from Member member1
        where member1.age >= (select avg(memberSub.age)
        from Member memberSub) */
    }

    /**
     *
     */
    @Test
    public void subQueyryIn() {

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        JPAExpressions
                                .select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();

        Assertions.assertThat(result).extracting("age")
                .containsExactly(20, 30, 40);

        /* select member1
        from Member member1
        where member1.age in (select memberSub.age
        from Member memberSub
        where memberSub.age > 101) */
    }

    @Test
    public void selectSubQuery() {
        QMember memberSub = new QMember("memberSub");

        List<Tuple> result = queryFactory
                .select(member.username,
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub))
                .from(member)
                .fetch();

        for(Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }

        /* select member1.username, (select avg(memberSub.age)
        from Member memberSub)
        from Member member1 */

        // 결과값
//        tuple = [member1, 25.0]
//        tuple = [member2, 25.0]
//        tuple = [member3, 25.0]
//        tuple = [member3, 25.0]
    }

    @Test
    public void basicCase() {
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for(String s : result) {
            System.out.println("s = " + s);
        }

          /* select
          case
          when member1.age = ?1 then ?2
          when member1.age = ?3 then ?4
          else '기타' end
            from Member member1 */
    }


    @Test
    public void complexCase() {
        List<String> result = queryFactory
                .select(
                        new CaseBuilder()
                                .when(member.age.between(0,20)).then("0~20살")
                                .when(member.age.between(21,30)).then("21~30살")
                                .otherwise("기타"))
                .from(member)
                .fetch();

        for(String s : result) {
            System.out.println("s = " + s);
        }

       /* select
       case when (member1.age between ?1 and ?2) then ?3
       when (member1.age between ?4 and ?5) then ?6
       else '기타' end
        from Member member1 */
    }


}
