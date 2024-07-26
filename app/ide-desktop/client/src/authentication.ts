/** @file Definition of the Electron-specific parts of the authentication flows of the IDE.
 *
 * # Overview of Authentication/Authorization
 *
 * Actions like creating projects, opening projects, uploading files to the cloud, etc. require the
 * user to be authenticated and authorized. Authenticated means that the user has an account that
 * the application recognizes, and that the user has provided their credentials to prove that they
 * are who they say they are. Authorized means that the user has the necessary permissions to
 * perform the action.
 *
 * Authentication and authorization are provided by the user logging in with their credentials,
 * which we exchange for a JSON Web Token (JWT). The JWT is sent with every HTTP request to the
 * backend.
 *
 * The authentication module of the dashboard and IDE handles these flows:
 * - registering a new user account,
 * - signing in to an existing user account (in exchange for an access token),
 * - signing out of the user account,
 * - setting the user's username (i.e., display name used in place of their email address),
 * - changing/resetting the user's password,
 * - etc.
 *
 * # Electron Inter-Process Communication (IPC)
 *
 * If the user is signing in through a federated identity provider (e.g., Google or GitHub), the
 * authentication flows need be able to to:
 * - redirect the user from the IDE to external sources (e.g., system web browser), and
 * - redirect the user from external sources to the IDE (e.g., system web browser, email client).
 *
 * The main Electron process can launch the system web browser. The dashboard and IDE are sandboxed,
 * so they can not launch the system web browser. By registering Inter-Process Communication (IPC)
 * listeners in the Electron app, we can bridge this gap, and allow the dashboad + IDE to emit
 * events that signal to the main Electron process to open URLs in the system web browser.
 *
 * ## Redirect To System Web Browser
 *
 * The user must use the system browser to complete sensitive flows such as signup and signin. These
 * flows should not be done in the app as the user cannot be expected to trust the app with their
 * credentials.
 *
 * To redirect the user from the IDE to an external source:
 * 1. Register a listener for {@link ipc.Channel.openUrlInSystemBrowser} IPC events.
 * 2. Emit an {@link ipc.Channel.openUrlInSystemBrowser} event. The listener registered in step
 * 1 will use the {@link opener} library to open the event's {@link URL}
 * argument in the system web browser, in a cross-platform way.
 *
 * ## Redirect To IDE
 *
 * The user must be redirected back to the IDE from the system web browser after completing a
 * sensitive flow such as signup or signin. The user may also be redirected to the IDE from an
 * external source such as an email client after verifying their email address.
 *
 * To handle these redirects, we use deep links. Deep links are URLs that are used to redirect the
 * user to a specific page in the application. To handle deep links, we use a custom URL protocol
 * scheme.
 *
 * To prepare the application to handle deep links:
 * - Register a custom URL protocol scheme with the OS (c.f., `electron-builder-config.ts`).
 * - Define a listener for Electron `OPEN_URL_EVENT`s.
 * - Define a listener for {@link ipc.Channel.openDeepLink} events (c.f., `preload.ts`).
 *
 * Then when the user clicks on a deep link from an external source to the IDE:
 * - The OS redirects the user to the application.
 * - The application emits an Electron `OPEN_URL_EVENT`.
 * - The `OPEN_URL_EVENT` listener checks if the {@link URL} is a deep link.
 * - If the {@link URL} is a deep link, the `OPEN_URL_EVENT` listener prevents Electron from
 * handling the event.
 * - The `OPEN_URL_EVENT` listener then emits an {@link ipc.Channel.openDeepLink} event.
 * - The {@link ipc.Channel.openDeepLink} listener registered by the dashboard receives the event.
 * Then it parses the {@link URL} from the event's {@link URL} argument. Then it uses the
 * {@link URL} to redirect the user to the dashboard, to the page specified in the {@link URL}'s
 * `pathname`. */
import * as fs from 'node:fs'
import * as os from 'node:os'
import * as path from 'node:path'

import * as electron from 'electron'
import opener from 'opener'

import * as common from 'enso-common'
import type * as dashboard from 'enso-dashboard'

import * as contentConfig from 'content-config'
import * as ipc from 'ipc'
import * as urlAssociations from 'url-associations'

const logger = contentConfig.logger

// ========================================
// === Initialize Authentication Module ===
// ========================================

/** Configure all the functionality that must be set up in the Electron app to support
 * authentication-related flows. Must be called in the Electron app `whenReady` event.
 * @param window - A function that returns the main Electron window. This argument is a lambda and
 * not a variable because the main window is not available when this function is called. This module
 * does not use the `window` until after it is initialized, so while the lambda may return `null` in
 * theory, it never will in practice. */
export function initAuthentication(window: () => electron.BrowserWindow) {
  // Listen for events to open a URL externally in a browser the user trusts. This is used for
  // OAuth authentication, both for trustworthiness and for convenience (the ability to use the
  // browser's saved passwords).
  electron.ipcMain.on(ipc.Channel.openUrlInSystemBrowser, (_event, url: string) => {
    logger.log(`Opening URL '${url}' in the default browser.`)
    opener(url)
  })

  // Listen for events to handle deep links.
  urlAssociations.registerUrlCallback(url => {
    logger.log(`Received 'open-url' event for '${url.toString()}'.`)
    if (url.protocol !== `${common.DEEP_LINK_SCHEME}:`) {
      logger.error(`'${url.toString()}' is not a deep link, ignoring.`)
    } else {
      logger.log(`'${url.toString()}' is a deep link, sending to renderer.`)
      window().webContents.send(ipc.Channel.openDeepLink, url.toString())
    }
  })

  // Listen for events to save the given user credentials to `~/.enso/credentials`.
  electron.ipcMain.on(
    ipc.Channel.saveAccessToken,
    (event, accessTokenPayload: dashboard.AccessToken | null) => {
      event.preventDefault()

      /** Home directory for the credentials file.  */
      const credentialsDirectoryName = `.${common.PRODUCT_NAME.toLowerCase()}`
      /** File name of the credentials file. */
      const credentialsFileName = 'credentials'
      /** System agnostic credentials directory home path. */
      const credentialsHomePath = path.join(os.homedir(), credentialsDirectoryName)

      if (accessTokenPayload == null) {
        try {
          fs.unlinkSync(path.join(credentialsHomePath, credentialsFileName))
        } catch {
          // Ignored, most likely the path does not exist.
        }
      } else {
        fs.mkdir(credentialsHomePath, { recursive: true }, error => {
          if (error) {
            logger.error(`Could not create '${credentialsDirectoryName}' directory.`)
          } else {
            fs.writeFile(
              path.join(credentialsHomePath, credentialsFileName),
              JSON.stringify({
                /* eslint-disable @typescript-eslint/naming-convention */
                client_id: accessTokenPayload.clientId,
                access_token: accessTokenPayload.accessToken,
                refresh_token: accessTokenPayload.refreshToken,
                refresh_url: accessTokenPayload.refreshUrl,
                expire_at: accessTokenPayload.expireAt,
                /* eslint-enable @typescript-eslint/naming-convention */
              }),
              innerError => {
                if (innerError) {
                  logger.error(`Could not write to '${credentialsFileName}' file.`)
                }
              },
            )
          }
        })
      }
    },
  )
}
