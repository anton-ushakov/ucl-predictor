package com.example.predictor.repository;

import com.example.predictor.domain.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    /** Сразу подгружаем пользователя, чтобы с ним можно было работать вне транзакции. */
    @Query("select t from AuthToken t join fetch t.user where t.tokenHash = :hash")
    Optional<AuthToken> findByTokenHashWithUser(@Param("hash") String hash);

    void deleteByTokenHash(String tokenHash);

    /** Отзывает все входы пользователя: после смены пароля старые телефоны должны войти заново. */
    @Modifying
    @Query("delete from AuthToken t where t.user.id = :userId")
    void deleteAllOfUser(@Param("userId") Long userId);
}
