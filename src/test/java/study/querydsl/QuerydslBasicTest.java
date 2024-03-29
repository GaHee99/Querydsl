package study.querydsl;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.team;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import jakarta.persistence.criteria.CriteriaBuilder.In;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
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


    //stringValue() -> enum처리할 때 자주 사용
    @Test
    public void constant() {
        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

        for(Tuple s : result) {
            System.out.println("Tuple = " + s);
        }

        //JPQL에서는 A안나가고, 결과 값에서만 상수가 추가된다.
        /* select member1.username
        from Member member1 */

    //    Tuple = [member1, A]
    //    Tuple = [member2, A]
    //    Tuple = [member3, A]
    //    Tuple = [member3, A]

    }

    @Test
    public void concat() { //stringValue()이용
        List<String> result = queryFactory.select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s); //s = member1_10
        }

        /* select concat(concat(member1.username,?1),str(member1.age))
        from Member member1
        where member1.username = ?2 */
    }


    @Test
    public void simpleProjection() {
        List<String> result = queryFactory
                .select(member.username) //Projection
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }


    @Test
    public void tupleProjection() {
        List<Tuple> result = queryFactory //Tuple은 repository딴에서만 사용하는 것을 권장, 되도록이면 DTO로 사용하자!
                .select(member.username, member.age)  //Projection
                .from(member)
                .fetch();

        for(Tuple tuple: result) {
            String userName = tuple.get(member.username);
            Integer age = tuple.get(member.age);

            System.out.println("username = " + userName);
            System.out.println("age = " + age);
        }
    }

    @Test
    public void findDtoByJPQL() {
        List<MemberDto> result  = em.createQuery(
                        "select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m", MemberDto.class)
                .getResultList();
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }

        /* select
        new study.querydsl.dto.MemberDto(m.username, m.age)
        from
        Member m */
    }

    @Test // setter을 이용한 projection
    public void findDtoBySetter() {
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age)) //bean : getter, setter하는 bean
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto" + memberDto);
        }
    }

    @Test //field를 이용한 projection
    public void findDtoByField() {
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }

        // 결과 값
//        memberDto = MemberDto(username=member1, age=10)
//        memberDto = MemberDto(username=member2, age=20)
//        memberDto = MemberDto(username=member3, age=30)
//        memberDto = MemberDto(username=member3, age=40)

        /* select member1.username, member1.age
            from Member member1 */
    }

    @Test
    public void findDtoByConstructor() {
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username, // 생성자의 파라미터 타입을 보고 Dto에 매칭된다.
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }

        // 결과 값
//        memberDto = MemberDto(username=member1, age=10)
//        memberDto = MemberDto(username=member2, age=20)
//        memberDto = MemberDto(username=member3, age=30)
//        memberDto = MemberDto(username=member3, age=40)

        /* select member1.username, member1.age
            from Member member1 */
    }

    @Test // DTO 와 entity의 필드명이 다를 경우 -> as 사용
    public void findByUserDto() {
        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"),
                        member.age))
                .from(member)
                .fetch();

        for(UserDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }

        //  member.usernam -> 의 경우 name = null 이 됨
//        memberDto = UserDto(name=null, age=10)
//        memberDto = UserDto(name=null, age=20)
//        memberDto = UserDto(name=null, age=30)
//        memberDto = UserDto(name=null, age=40)
    }


    // Projection에 subQuery 사용 -> ExpressionUtils 사용
    @Test
    public void findByUserDtoWithExpressionUrils() {
        QMember memberSub = new QMember("memberSub");

        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"),
                        ExpressionUtils.as(JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub), "age")
                        )
                )
                .from(member)
                .fetch();

        for(UserDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }

        // result
//        memberDto = UserDto(name=member1, age=40)
//        memberDto = UserDto(name=member2, age=40)
//        memberDto = UserDto(name=member3, age=40)
//        memberDto = UserDto(name=member3, age=40)

        /* select member1.username as name, (select max(memberSub.age)
        from Member memberSub) as age
        from Member member1 */
    }


    // 장점 : dto필드에 대한 컴파일 오류를 잘 잡을 수 있다.
    // 단점 : 1. Qfile생성해야한다.
    //       2. 코드 의존성이 높아진다. (memberDto가 Querydsl 라이브러리의 영향을받는다.)
    @Test
    public void findDtoByQueryProjection() {
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }


    /** 동적 쿼리 - BooleanBuilder사용 ***/
    @Test
    public void dynamicQuery_BooleanBuilder() {
        String usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember1(usernameParam, ageParam);
        Assertions.assertThat(result.size()).isEqualTo(1);
    }

    // 파라미터 값이 null인지 아닌지에 따라서 쿼리가 동적으로 바뀌어야 하는 상황
    private List<Member> searchMember1(String usernameCond, Integer ageCond) {
        BooleanBuilder builder = new BooleanBuilder();
//        BooleanBuilder builder = new BooleanBuilder(member.username.eq(usernameCond)); 초기 조건을 넣을 수 있다. (무조건 들어가야 하는 값일 경우)

        if (usernameCond != null) {
            builder.and(member.username.eq(usernameCond)); // usernameCond에 값이 있으면 'and'조건을 넣어준다.
        }

        if (ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }

        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();

        /* select
        member1
        from
            Member member1
        where
            member1.username = ?1
        and member1.age = ?2 */
    }


    /** 동적 쿼리 - Where 다중 파라미터 사용 ***/
    // 김영한님이 실무에서 정말 좋아하는 방법! -> 코드가 깔끔해진다.
    @Test
    public void dynamicQueyr_whereParam() {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);

        /* select member1
        from Member member1
        where member1.username = ?1

        or

        select member1
        from Member member1
        where member1.username = ?1 and member1.age = ?2 */
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
                .where(usernameEq(usernameCond), ageEq(ageCond)) //여기에서 바로 해결, where의 인자로 Null이 들어가면 아무일도 일어나지 않는다.
                                                                 //따라서 동적 쿼리가 만들어진다.
                .fetch();

        //or
//        return queryFactory
//                .selectFrom(member)
//                .where(allEq(usernameCond, ageCond)) // 동적 쿼리를 조립할 수 있다.
//                //따라서 동적 쿼리가 만들어진다.
//                .fetch();
    }

    private BooleanExpression usernameEq(String usernameCond) {
        return usernameCond != null ? member.username.eq(usernameCond) : null;
    }

    // ex) 광고 상태 isValid, 날짜가 In~~ 등등 condition => isServicable으로 조립하기 쉬워진다.
    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    // 한번에 BooleanBuilder을 조림할 수 있다.
    private BooleanExpression allEq(String usernameCond, Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }



    /** 수정, 삭제 벌크 연산(배치 쿼리) **/
    // 벌크 연산 후 영속성 컨텍스트를 초기화하자!
    // em.flush()
    // em.clear()
    @Test
    @Commit
    public void bulkUpdateWithNoFlush() {
        // 영속성 컨텍스트 + DB
        // member1 = 10 -> DB member1
        // member2 = 20 -> DB member2
        // member3 = 30 -> DB member3
        // member4 = 40 -> DB member4

        // 벌크연산
        long count = queryFactory //count 에는 영향을 받은 row수가 나온다.
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();


        // 실행 후DB -> 영속성 컨텍스트와 값이 매칭이 안된다.
        // member1 = 10 -> DB 비회원
        // member2 = 20 -> DB 비회원
        // member3 = 30 -> DB member3
        // member4 = 40 -> DB member3

        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();

        for (Member member1 : result) {
            System.out.println("member1 = " + member1);

            //실행 값 -> 쿼리는 잘 나가지만, 영속성 컨텍스트가 우선권을 갖기 때문에 실행 결과 값이 db와 다름
//            member1 = Member(id=1, username=member1, age=10)
//            member1 = Member(id=2, username=member2, age=20)
//            member1 = Member(id=3, username=member3, age=30)
//            member1 = Member(id=4, username=member3, age=40)

             /* select
                member1
            from
                Member member1 */

        }

    }


    @Test
    @Commit
    public void bulkUpdateWithClear() {
        // 벌크연산
        long count = queryFactory //count 에는 영향을 받은 row수가 나온다.
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();
        em.flush();
        em.clear();

        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();

        for (Member member1 : result) {
            System.out.println("member1 = " + member1);

//            member1 = Member(id=1, username=비회원, age=10)
//            member1 = Member(id=2, username=비회원, age=20)
//            member1 = Member(id=3, username=member3, age=30)
//            member1 = Member(id=4, username=member3, age=40)

             /* select
                member1
            from
                Member member1 */

        }
    }


    @Test
    public void bulkAdd() {
        queryFactory
                .update(member)
                .set(member.age, member.age.add(1)) // 뺼셈은 1 넣기  , minus()없음
            //  .set(meber.age, member.age.multiply(1)) // 곱셈
                .execute();

        /* update
        Member member1
            set
        member1.age = member1.age + ?1 */
    }

    @Test
    public void bulkDelete() {
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();

         /* delete
        from
            Member member1
        where
        member1.age > ?1 */
    }


    /** SQL function 호출하기 **/
    // member의 username중의 member라는 단어를 M으로 바꾸기
    @Test
    public void sqlFunction() {
        List<String> result = queryFactory
                .select(
                        Expressions.stringTemplate("function('replace', {0}, {1}, {2})",
                                member.username, "member", "M"))
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);

            //result
//            s = M1
//            s = M2
//            s = M3
//            s = M3
        }

        // SQL
//        select
//        replace(m1_0.username, ?, ?)
//        from
//        member m1_0

        // JPQL
        /* select
        function('replace', member1.username, ?1, ?2)
            from
        Member member1 */
    }


    //소문자 바꾸기, -> 쿼리 dsl에 내장함수 쓰는게 낫다.
    @Test
    public void sqlFunction2() {
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
//                .where(member.username.eq(
//                        Expressions.stringTemplate("function('lower', {0})", member.username)))
                .where(member.username.eq(member.username.lower()))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s );
        }

        //SQL
//        select
//        m1_0.username
//                from
//        member m1_0
//        where
//        m1_0.username=lower(m1_0.username)

        // JPQL
         /* select
        member1.username
    from
        Member member1
    where
        member1.username = function('lower', member1.username) */
    }


}
