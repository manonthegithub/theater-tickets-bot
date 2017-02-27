import java.nio.file.Paths
import java.time.LocalDate

"111sdcscd".drop(2)

val pattern = """^year(\d{4})-month(\d{1,2}.+)""".r
"year2017-month01" match {
  case pattern(a, v) => println(a + " " + v)
}


LocalDate.parse("2000-01-05").getMonth

None.toString
val p = Paths.get("test")
p.toAbsolutePath

val s = " scdsd scDFFsd    \n  dsc"
s.toLowerCase.replaceAll("\\s", "")