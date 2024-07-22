val isOnCI = System.getenv()["GITHUB_ACTIONS"] != null

allprojects {
    group = "dev.hsbrysk"
    version = "1.1.1" + if (isOnCI) "" else "-SNAPSHOT"
}
