package com.cyborg

import cats._, cats.data._, cats.implicits._
import doobie.imports._
import fs2.interop.cats._

import shapeless._
import shapeless.record.Record

object database {

  val superdupersecretPassword = ""

  val xa = DriverManagerTransactor[IOLite](
    "org.postgresql.Driver",
    "jdbc:postgresql:world",
    "postgres",
    s"$superdupersecretPassword"
  )

  case class Cunt(code: String, name: String, pop: Int, gnp: Option[Double])

  def biggerThan(minPop: Int) = sql"""
      select code, name, population, gnp
      from country
      where population > $minPop
    """.query[Cunt]


  def populationIn(range: Range) = sql"""
      select code, name, population, gnp
      from country
      where population > ${range.min}
      and population < ${range.max}
    """.query[Cunt]


  def populationInF(range: Range, codes: NonEmptyList[String]) = {
    val q = fr"""
      select code, name, population, gnp
      from country
      where population > ${range.min}
      and population < ${range.max}
      and """ ++ Fragments.in(fr"code", codes)
    q.query[Cunt]
  }

  val meme = biggerThan(150000000)
    .process
    .take(5)
    .list
    .transact(xa)
    .unsafePerformIO
    .take(5)
    .map(_.toString)
    // .map(_ => 1)


  val meme2 = populationIn(Range(150000000, 200000000))
    .process
    .take(5)
    .list
    .transact(xa)
    .unsafePerformIO
    .take(5)
    .map(_.toString)


  val meme3 = populationInF(
    Range(100000000, 300000000),
    NonEmptyList.of("USA", "BRA", "PAK", "GBR"))
    .process
    .take(5)
    .list
    .transact(xa)
    .unsafePerformIO
    .take(5)
    .map(_.toString)


  val drop : Update0 =
    sql"""
        drop table if exists person
    """.update

  val create: Update0 =
    sql"""
        create table person (
          id serial,
          name varchar not null unique,
          age smallint
        )
    """.update


  def makePerson = (drop.run *> create.run)

  def insert1(name: String, age: Option[Short]): Update0 =
    sql"insert into person (name, age) values ($name, $age)".update

  case class Person(id: Long, name: String, age: Option[Short])

  val DELET_THIS = {
    makePerson.transact(xa).unsafePerformIO
    insert1("Alice", Some(12)).run.transact(xa).unsafePerformIO
    insert1("Bob", Some(14)).run.transact(xa).unsafePerformIO

    sql"update person set age = 13 where name = 'Alice'"
      .update
      .run
      .transact(xa)
      .unsafePerformIO
  }

  val getDb =
    sql"select id, name, age from person".query[Person]
      .process
      .take(1000)
      .list
      .transact(xa)

  def insert2(name: String, age: Option[Short]): ConnectionIO[Person] =
    for {
      _  <- sql"insert into person (name, age) values ($name, $age)".update.run
      id <- sql"select lastval()".query[Long].unique
      p  <- sql"select id, name, age from person where id = $id".query[Person].unique
    } yield p

  val DELEET = {
    insert2("my brother who works for steam, who is going to ban u. DELEDDDDNts", Some(17))
      .transact(xa)
  }

  def insert3(name: String, age: Option[Short]): ConnectionIO[Person] =
    sql"insert into person (name, age) values ($name, $age)"
      .update.withUniqueGeneratedKeys("id", "name", "age")

  val shrieks = {
    insert3("[Autistic shrieking]", None)
      .transact(xa)
  }

  val up = sql"update person set age = age + 1 where age is not null"
    .update
    .withUniqueGeneratedKeys[Person]("id", "name", "age")
    .transact(xa)

  type PersonInfo = (String, Option[Short])
  def insertMany(ps: List[PersonInfo]): ConnectionIO[Int] = {
    val sql = "insert into person (name, age) values (?, ?)"
    Update[PersonInfo](sql).updateMany(ps)
  }

  def insertMany2(ps: List[PersonInfo]): fs2.Stream[ConnectionIO, Person] = {
    val sql = "insert into person (name, age) values (?, ?)"
    Update[PersonInfo](sql).updateManyWithGeneratedKeys[Person]("id", "name", "age")(ps)
  }

  val data = List[PersonInfo](
    ("Flems dad", None),
    ("Flem", Some(12))
  )

  val theMany = insertMany(data).transact(xa)
  val theMany2 = insertMany2(data)
    .take(5)
    .list
    .transact(xa)
}
