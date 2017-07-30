//     Project: sbt-node
//      Module:
// Description:
package de.surfice.sbtnpm.sass

import de.surfice.sbtnpm.NpmPlugin
import de.surfice.sbtnpm.NpmPlugin.autoImport._
import de.surfice.sbtnpm.utils.{ExternalCommand, NodeCommand}
import org.scalajs.sbtplugin.{ScalaJSPluginInternal, Stage}
import sbt._
import Keys._
import Cache._
import de.surfice.sbtnpm.assets.AssetsPlugin

object SassPlugin extends AutoPlugin {

  override lazy val requires = NpmPlugin && AssetsPlugin

  /**
   * @groupname tasks Tasks
   * @groupname settings Settings
   */
  object autoImport {
    /**
     * Version of the `node-sass` npm module.
     */
//    val nodeSassVersion: SettingKey[String] =
//      settingKey[String]("node-sass version")

    /**
     * Defines the node-sass command to be used
     */
    val sassCommand: SettingKey[NodeCommand] =
      settingKey[NodeCommand]("node-sass command")

    val sassTarget: SettingKey[File] =
      settingKey[File]("target directory for compiled sass files (may be scoped to fastOptJS and fullOptJS)")

    val sassSourceDirectories: SettingKey[Seq[File]] =
      settingKey[Seq[File]]("list of source directories that contain files to be processed by sass (may be scoped to fastOptJS and fullOptJS)")

    val sassInputs: TaskKey[Seq[Attributed[(File,String)]]] =
      taskKey("Contains all sass input files to be processed (may be scoped to fastOptJS and fullOptJS)")

    val sass: TaskKey[Long] =
      taskKey[Long]("Runs the sass compiler")
  }

  import autoImport._
  import AssetsPlugin.autoImport._

  override lazy val projectSettings: Seq[Def.Setting[_]] = Seq(
//    nodeSassVersion := "~4.5.2",

    sassCommand := NodeCommand(npmNodeModulesDir.value,"node-sass","node-sass")

//    defineSassSourceDirectories(Compile),

//    defineSassTarget(Compile),

//    defineSassInputs(Compile),

//    defineSassTask(Compile,nodeSassTarget in Compile),

//    npmDevDependencies += "node-sass" -> nodeSassVersion.value
  ) ++
    perScalaJSStageSettings(Stage.FullOpt) ++
    perScalaJSStageSettings(Stage.FastOpt)


  private def perScalaJSStageSettings(stage: Stage): Seq[Def.Setting[_]] = {

    val stageTask = ScalaJSPluginInternal.stageKeys(stage)

    Seq(
      defineSassSourceDirectories(stageTask),
      defineSassTarget(stageTask),
      defineSassInputs(stageTask),
      defineSassTask(stageTask)
    )
  }

  private def defineSassTask(scoped: Scoped) = {
    val (task, inputs, targetDir) = (sass in scoped, sassInputs in scoped, sassTarget in scoped)
    task := {
      npmInstall.value
      val lastrun = task.previous
      val cwd = targetDir.value
      if(!cwd.exists())
        IO.createDirectory(cwd)
      val sass = sassCommand.value
      val logger = streams.value.log
      var modified = false
      inputs.value.foreach{ f =>
        val (src,relDest) = f.data
        val dest = cwd / relDest
        if(lastrun.isEmpty || !dest.exists() || src.lastModified()>lastrun.get) {
          sass.run(src.getAbsolutePath, dest.getAbsolutePath)(cwd,logger)
          modified = true
        }
      }
      if(modified)
        new java.util.Date().getTime
      else
        lastrun.get
    }
  }

  private def defineSassInputs(scoped: Scoped) = {
    val (task,sourceDirs) = (sassInputs in scoped,sassSourceDirectories in scoped)
    task := {
      Attributed.blankSeq( sourceDirs.value flatMap { dir =>
        val fs = (dir ** "*").get.filter{ f =>
          val name = f.getName
          f.isFile && !name.startsWith("_") && ( name.endsWith(".scss") || name.endsWith(".sass") || name.endsWith(".css") )
        }
        fs.map{ f =>
          val path = f.relativeTo(dir).get.getPath

          (f, path.substring(0,path.lastIndexOf("."))+".css" )
        }
      })
    }
  }

  private def defineSassSourceDirectories(scoped: Scoped) = {
      sassSourceDirectories in scoped := (sourceDirectories in (NodeAssets,scoped)).value
      .map( _ / "sass" )
  }

  private def defineSassTarget(scoped: Scoped) =
    sassTarget in scoped := (crossTarget in (NodeAssets,scoped)).value / "css"

}
