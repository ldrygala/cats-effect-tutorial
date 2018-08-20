/*
 * Copyright (c) 2018 Luis Rodero-Merino
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tutorial

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._ 

import java.io._ 

object CopyFile extends IOApp {

  def transmit(origin: InputStream, destination: OutputStream, buffer: Array[Byte], acc: Long): IO[Long] =
    for {
      _      <- IO.cancelBoundary // Cancelable at each iteration
      amount <- IO{ origin.read(buffer, 0, buffer.size) }
      total  <- if(amount > -1) IO { destination.write(buffer, 0, amount) } *> transmit(origin, destination, buffer, acc + amount)
                else IO.pure(acc) // End of read stream reached (by java.io.InputStream contract), nothing to write
    } yield total // Returns the actual amount of bytes transmitted

  def transfer(origin: InputStream, destination: OutputStream): IO[Long] =
    for {
      buffer <- IO{ new Array[Byte](1024 * 10) } // Allocated only when the IO is evaluated
      acc    <- transmit(origin, destination, buffer, 0L)
    } yield acc

  def copy(origin: File, destination: File): IO[Long] = {
    val in: IO[InputStream]  = IO{ new BufferedInputStream(new FileInputStream(origin)) }
    val out:IO[OutputStream] = IO{ new BufferedOutputStream(new FileOutputStream(destination)) }

    (in, out)                  // Stage 1: Getting resources 
      .tupled                  // From (IO[InputStream], IO[OutputStream]) to IO[(InputStream, OutputStream)]
      .bracket{
        case (in, out) =>
          transfer(in, out)    // Stage 2: Using resources (for copying data, in this case)
      } {
        case (in, out) =>      // Stage 3: Freeing resources
          (IO{in.close()}, IO{out.close()})
          .tupled              // From (IO[Unit], IO[Unit]) to IO[(Unit, Unit)]
          .handleErrorWith(_ => IO.unit) *> IO.unit
      }
  }

  // The 'main' function of IOApp //
  override def run(args: List[String]): IO[ExitCode] =
    for {
      _      <- if(args.length < 2) IO.raiseError(new IllegalArgumentException("Need origin and destination files"))
                else IO.unit
      orig = new File(args(0))
      dest = new File(args(1))
      copied <- copy(orig, dest)
      _      <- IO{ println(s"$copied bytes copied from ${orig.getPath} to ${dest.getPath}") }
    } yield ExitCode.Success

}
