package net.freefeed.kcabend.api

import com.auth0.jwt.JWTSigner
import com.auth0.jwt.JWTVerifier
import net.freefeed.kcabend.model.Feeds
import net.freefeed.kcabend.model.ForbiddenException
import net.freefeed.kcabend.model.User
import org.mindrot.BCrypt
import java.security.SignatureException

public class Authenticator(private val feeds: Feeds, val secret: String) {
    fun createUser(userName: String, password: String, private: Boolean = false): User {
        val encryptedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
        return feeds.users.createUser(userName, encryptedPassword, private)
    }

    fun verifyPassword(userName: String, password: String): String {
        val user = feeds.users.findByUserName(userName)
        if (user !is User || user.encryptedPassword == null || !BCrypt.checkpw(password, user.encryptedPassword)) {
            throw ForbiddenException()
        }
        return JWTSigner(secret).sign(hashMapOf("userId" to user.id))
    }

    fun verifyAuthToken(authToken: String): User {
        try {
            val payload = JWTVerifier(secret).verify(authToken)
            val userId = payload["userId"] as Int? ?: throw ForbiddenException()
            val user = feeds.users[userId]
            if (user !is User || user.encryptedPassword == null) {
                throw ForbiddenException()
            }
            return user
        } catch(e: SignatureException) {
            throw ForbiddenException()
        } catch(e: IllegalStateException) {
            throw ForbiddenException()
        }
    }
}
