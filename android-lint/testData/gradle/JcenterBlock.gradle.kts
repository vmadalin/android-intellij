buildscript {
  repositories {
    <warning>jcenter {
      content {
        excludeGroupByRegex("^my\\.company.*")
      }
    }</warning>
  }
}
