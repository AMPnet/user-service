package com.ampnet.userservice.grpc

import com.ampnet.userservice.enums.UserRole
import com.ampnet.userservice.exception.ErrorCode
import com.ampnet.userservice.exception.InvalidRequestException
import com.ampnet.userservice.exception.ResourceNotFoundException
import com.ampnet.userservice.persistence.model.User
import com.ampnet.userservice.persistence.repository.UserInfoRepository
import com.ampnet.userservice.persistence.repository.UserRepository
import com.ampnet.userservice.proto.Empty
import com.ampnet.userservice.proto.GetUserRequest
import com.ampnet.userservice.proto.GetUsersRequest
import com.ampnet.userservice.proto.SetRoleRequest
import com.ampnet.userservice.proto.UserResponse
import com.ampnet.userservice.proto.UserServiceGrpc
import com.ampnet.userservice.proto.UserWithInfoResponse
import com.ampnet.userservice.proto.UsersResponse
import com.ampnet.userservice.service.AdminService
import com.ampnet.userservice.service.impl.ServiceUtils
import io.grpc.stub.StreamObserver
import mu.KLogging
import net.devh.boot.grpc.server.service.GrpcService
import java.util.UUID

@GrpcService
class GrpcUserServer(
    private val userRepository: UserRepository,
    private val userInfoRepository: UserInfoRepository,
    private val adminService: AdminService
) : UserServiceGrpc.UserServiceImplBase() {

    companion object : KLogging()

    override fun getUsers(request: GetUsersRequest, responseObserver: StreamObserver<UsersResponse>) {
        logger.debug { "Received gRPC request: GetUsersRequest" }

        val uuids = request.uuidsList.mapNotNull {
            try {
                UUID.fromString(it)
            } catch (ex: IllegalArgumentException) {
                logger.warn(ex.message)
                null
            }
        }
        val users = userRepository.findAllById(uuids)

        val usersResponse = users.map { buildUserResponseFromUser(it) }

        logger.debug { "UsersResponse: $usersResponse" }
        val response = UsersResponse.newBuilder()
            .addAllUsers(usersResponse)
            .build()
        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    override fun setUserRole(request: SetRoleRequest, responseObserver: StreamObserver<UserResponse>) {
        logger.info { "Received gRPC request setUserRole: $request" }
        try {
            val userUuid = UUID.fromString(request.uuid)
            val role = getRole(request.role)
            val user = adminService.changeUserRole(userUuid, role, request.coop)
            logger.info { "Successfully set new role: $role to user: ${user.uuid}" }
            responseObserver.onNext(buildUserResponseFromUser(user))
            responseObserver.onCompleted()
        } catch (ex: IllegalArgumentException) {
            logger.warn(ex) { "Could not get new user role values" }
            responseObserver.onError(ex)
        } catch (ex: InvalidRequestException) {
            logger.warn(ex) { "Missing user to change his role" }
            responseObserver.onError(ex)
        }
    }

    override fun getPlatformManagers(request: Empty, responseObserver: StreamObserver<UsersResponse>) {
        logger.debug { "Received gRPC request getPlatformManagers: $request" }
        val platformManagers = adminService
            .findByRoles(listOf(UserRole.ADMIN, UserRole.PLATFORM_MANAGER))
            .map { buildUserResponseFromUser(it) }
        val response = UsersResponse.newBuilder()
            .addAllUsers(platformManagers)
            .build()
        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    override fun getTokenIssuers(request: Empty, responseObserver: StreamObserver<UsersResponse>) {
        logger.debug { "Received gRPC request getTokenIssuers: $request" }
        val tokenIssuers = adminService
            .findByRoles(listOf(UserRole.ADMIN, UserRole.TOKEN_ISSUER))
            .map { buildUserResponseFromUser(it) }
        val response = UsersResponse.newBuilder()
            .addAllUsers(tokenIssuers)
            .build()
        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    override fun getUserWithInfo(request: GetUserRequest, responseObserver: StreamObserver<UserWithInfoResponse>) {
        logger.debug { "Received gRPC request getUserWithInfo: $request" }
        ServiceUtils.wrapOptional(userRepository.findById(UUID.fromString(request.uuid)))
            ?.let { user ->
                logger.debug { "User : $user" }
                responseObserver.onNext(buildUserWithInfoResponseFromUser(user))
                responseObserver.onCompleted()
                return
            }
        logger.info { "Could find user with uuid: ${request.uuid}" }
        responseObserver.onError(
            ResourceNotFoundException(ErrorCode.USER_MISSING, "Missing user with uuid: $request.uuid")
        )
    }

    private fun getRole(role: SetRoleRequest.Role): UserRole =
        when (role) {
            SetRoleRequest.Role.ADMIN -> UserRole.ADMIN
            SetRoleRequest.Role.PLATFORM_MANAGER -> UserRole.PLATFORM_MANAGER
            SetRoleRequest.Role.TOKEN_ISSUER -> UserRole.TOKEN_ISSUER
            SetRoleRequest.Role.USER -> UserRole.USER
            else -> throw IllegalArgumentException("Invalid user role")
        }

    fun buildUserResponseFromUser(user: User): UserResponse =
        UserResponse.newBuilder()
            .setUuid(user.uuid.toString())
            .setEmail(user.email)
            .setFirstName(user.firstName)
            .setLastName(user.lastName)
            .setEnabled(user.enabled)
            .setCoop(user.coop)
            .build()

    fun buildUserWithInfoResponseFromUser(user: User): UserWithInfoResponse {
        val builder = UserWithInfoResponse.newBuilder()
            .setUser(buildUserResponseFromUser(user))
        user.userInfoId?.let {
            ServiceUtils.wrapOptional(userInfoRepository.findById(it))?.let { userInfo ->
                builder.address = userInfo.address
                builder.createdAt = userInfo.createdAt.toInstant().toEpochMilli()
            }
        }
        return builder.build()
    }
}
