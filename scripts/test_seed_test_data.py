import unittest

from scripts.seed_test_data import build_question_search_path, build_students, find_exact_question, knowledge_codes_for_question


class ShowcaseStudentFixtureTest(unittest.TestCase):
    def test_generates_a_balanced_reproducible_cohort(self):
        first = build_students()
        second = build_students()

        self.assertEqual(first, second)
        self.assertEqual(len(first), 60)
        self.assertEqual(len({row[0] for row in first}), len(first))
        self.assertEqual(sum(row[3] == "高一(1)班" for row in first), 30)
        self.assertEqual(sum(row[3] == "高一(2)班" for row in first), 30)

    def test_question_search_path_percent_encodes_chinese_text(self):
        path = build_question_search_path("下列物理量")

        self.assertTrue(path.isascii())
        self.assertIn("search=%E4%B8%8B", path)

    def test_duplicate_question_lookup_requires_full_content_match(self):
        rows = [
            {"id": 10, "content": "关于参考系的旧题目"},
            {"id": 20, "content": "关于参考系，下列说法正确的是（　　）"},
            {"id": 11, "content": "关于参考系，下列说法正确的是（　　）"},
        ]

        self.assertEqual(find_exact_question(rows, "关于参考系，下列说法正确的是（　　）")["id"], 11)
        self.assertIsNone(find_exact_question(rows, "关于参考系，不同的说法"))

    def test_physics_questions_receive_existing_curriculum_point_codes(self):
        self.assertEqual(
            knowledge_codes_for_question({"content": "关于惯性，下列说法正确的是（　　）"}),
            ["mechanics.inertia"],
        )


if __name__ == "__main__":
    unittest.main()
