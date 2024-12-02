package ch.sbb.polarion.extension.aad.synchronizer.filter;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemberFilterTest {

    private static Stream<Arguments> provideFilterTestData() {
        return Stream.of(
                // No filter
                testData(
                        inputMembers("member1", "member2", "member3"),
                        null,
                        null,
                        expectedMembers("member1", "member2", "member3")
                ),
                testData(
                        inputMembers("member1", "member2", "member3"),
                        whitelist(null, null),
                        blacklist(null, null),
                        expectedMembers("member1", "member2", "member3")
                ),
                testData(
                        inputMembers("member1", "member2", "member3"),
                        whitelist("", null),
                        blacklist("", null),
                        expectedMembers("member1", "member2", "member3")
                ),
                testData(
                        inputMembers("member1", "member2", "member3"),
                        whitelist(null, accounts()),
                        blacklist(null, accounts()),
                        expectedMembers("member1", "member2", "member3")
                ),
                testData(
                        inputMembers("member1", "member2", "member3"),
                        whitelist("", accounts()),
                        blacklist("", accounts()),
                        expectedMembers("member1", "member2", "member3")
                ),

                // Regexp filter
                testData(
                        inputMembers("admin1", "admin2", "user1", "user2"),
                        whitelist("^user\\d{1}$", accounts()),
                        blacklist("", accounts()),
                        expectedMembers("user1", "user2")
                ),
                testData(
                        inputMembers("admin1", "admin2", "user1", "user2"),
                        whitelist("^user\\d{1}$", accounts()),
                        blacklist("user2", accounts()),
                        expectedMembers("user1")
                ),
                testData(
                        inputMembers("admin1", "admin2", "user1", "user2"),
                        whitelist("", accounts()),
                        blacklist("admin.*", accounts()),
                        expectedMembers("user1", "user2")
                ),
                testData(
                        inputMembers("admin1", "admin2", "user1", "user2"),
                        whitelist("^admin\\d{1}$", accounts()),
                        blacklist("admin2", accounts()),
                        expectedMembers("admin1")
                ),
                testData(
                        inputMembers("admin1", "admin2", "user1", "user2"),
                        whitelist("^.*\\d{1}$", accounts()),
                        blacklist("admin2", accounts()),
                        expectedMembers("admin1", "user1", "user2")
                ),

                // Account list
                testData(
                        inputMembers("member1", "member2", "member3"),
                        whitelist(null, accounts("member1", "member3")),
                        blacklist(null, null),
                        expectedMembers("member1", "member3")
                ),
                testData(
                        inputMembers("member1", "member2", "member3"),
                        whitelist(null, null),
                        blacklist(null, accounts("member2")),
                        expectedMembers("member1", "member3")
                ),
                testData(
                        inputMembers("member1", "member2", "member3"),
                        whitelist(null, accounts("member1", "member3")),
                        blacklist(null, accounts("member3")),
                        expectedMembers("member1")
                ),

                // Combination
                testData(
                        inputMembers(
                                "userAAA@example.com",
                                "userBBB@example.com",
                                "userCCC@example.com",
                                "userDDD@example.com",
                                "user001@example.com",
                                "user002@example.com",
                                "user003@example.com",
                                "user004@example.com",
                                "manager001@example.com",
                                "manager002@example.com",
                                "admin@example.com",
                                "testadmin@example.com",
                                "guest@example.com",
                                "service@example.com",
                                "root@example.com",
                                "unknown@example.com"
                        ),
                        whitelist("^(user|manager)\\d{3}@example\\.com$", accounts("userAAA@example.com", "userBBB@example.com", "unknown@example.com")),
                        blacklist("^(admin|guest|service)@example\\.com$", accounts("testadmin@example.com", "root@example.com")),
                        expectedMembers(
                                "userAAA@example.com",
                                "userBBB@example.com",
                                "user001@example.com",
                                "user002@example.com",
                                "user003@example.com",
                                "user004@example.com",
                                "manager001@example.com",
                                "manager002@example.com",
                                "unknown@example.com"
                        )
                )
        );
    }

    @ParameterizedTest
    @MethodSource("provideFilterTestData")
    void testFilterMembers(List<String> inputMembers, Whitelist whitelist, Blacklist blacklist, List<String> expectedOutput) {
        MemberFilter memberFilter = new MemberFilter(whitelist, blacklist);
        List<String> actualOutput = memberFilter.filterMembers(inputMembers);

        assertEquals(
                expectedOutput.stream().sorted().toList(),
                actualOutput.stream().sorted().toList()
        );
    }

    private static @NotNull Arguments testData(List<String> inputMembers, Whitelist whitelist, Blacklist blacklist, List<String> expectedOutput) {
        return Arguments.of(inputMembers, whitelist, blacklist, expectedOutput);
    }

    private static List<String> inputMembers(String... members) {
        return List.of(members);
    }

    private static Whitelist whitelist(String filter, List<String> accounts) {
        Map<String, Object> map = new HashMap<>();
        if (filter != null) {
            map.put(FilterList.FILTER_NAME, filter);
        }
        if (accounts != null) {
            map.put(FilterList.ACCOUNTS_NAME, accounts);
        }
        return new Whitelist(map.isEmpty() ? null : map);
    }

    private static Blacklist blacklist(String filter, List<String> accounts) {
        Map<String, Object> map = new HashMap<>();
        if (filter != null) {
            map.put(FilterList.FILTER_NAME, filter);
        }
        if (accounts != null) {
            map.put(FilterList.ACCOUNTS_NAME, accounts);
        }
        return new Blacklist(map.isEmpty() ? null : map);
    }

    private static List<String> accounts(String... accounts) {
        return List.of(accounts);
    }

    private static List<String> expectedMembers(String... members) {
        return List.of(members);
    }

}
